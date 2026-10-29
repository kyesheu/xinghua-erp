package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.ai.service.ToolToken;
import cn.xinghuaerp.ai.service.impl.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 工具网关：Python 侧决定「查什么」，真正的查询仍在这里执行。
 *
 * <p>约束有三层：只接受本机调用、共享密钥必须匹配、工具令牌必须能解出有效用户。
 * 查询还必须按实时菜单权限与业务主体过滤，令牌校验本身不代表数据授权。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/ai-tools")
public class AiToolGateway {

    /** 单次返回条数上限：模型要的是判断依据，不是全表导出。 */
    private static final int MAX_LIMIT = 500;

    private final PythonAgentClient python;
    private final ToolToken toolToken;
    private final cn.xinghuaerp.system.service.BusinessAccessService access;
    private final cn.xinghuaerp.system.service.AiAgentPermissionService permissions;
    private final cn.xinghuaerp.ai.service.AiInventoryReadService scopedInventory;
    private final GoodsTools goodsTools;
    private final OrderTools orderTools;
    private final RefundTools refundTools;
    private final InventoryTools inventoryTools;
    private final ShopTools shopTools;
    private final PurchaseTools purchaseTools;
    private final MemberTools memberTools;
    private final SupplierTools supplierTools;
    private final LogisticsTools logisticsTools;
    private final WarehouseTools warehouseTools;
    private final StockFlowTools stockFlowTools;

    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody ToolQuery query,
                                     @RequestHeader(value = "X-Agent-Secret", defaultValue = "") String secret,
                                     @RequestHeader(value = "X-Tool-Token", defaultValue = "") String token,
                                     HttpServletRequest request) {
        requireLoopback(request);
        requireSecret(secret);
        long userId = verifyToken(token);
        if (query == null || query.resource() == null || query.resource().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少查询类型");
        }
        Map<String, Object> params = query.params() == null ? Map.of() : query.params();
        if (params.size() > 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询参数过多");
        String text = execute(query.resource().trim(), params, userId);
        if (text != null && text.length() > 32000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询资料过大，请缩小范围");
        log.info("AI 工具查询 userId={} resource={}", userId, query.resource());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resource", query.resource());
        result.put("text", text == null ? "" : text);
        return result;
    }

    /** 白名单分发：新增工具必须同时改这里与 Python 侧的工具说明，不接受任意方法名。 */
    private String execute(String resource, Map<String, Object> p, long userId) {
        String permission = switch (resource) {
            case "shop.list", "order.list" -> "ai:sales:query";
            case "refund.list" -> "ai:risk:query";
            case "member.list" -> "ai:marketing:query";
            case "goods.search", "goods.sku" -> "ai:product:query";
            case "inventory.list", "warehouse.list", "stock.in", "stock.out" -> "ai:inventory:query";
            case "purchase.list", "supplier.list", "logistics.waybill" -> "ai:purchase:query";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的数据查询");
        };
        if (!permissions.allows(userId, permission))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无对应 AI 资料查询权限");
        var subject = access.scope(userId);
        boolean headquarters = "HEADQUARTERS".equals(subject.type());
        // 复用已校验所有权的库存/商品读模型，不能回退到全局工具。
        if (resource.equals("inventory.list")) {
            return inventoryTools.getInventoryList(integer(p, "availableLte"), limit(p, 100), subject);
        }
        if (resource.equals("goods.search") || resource.equals("goods.sku")) {
            return cn.xinghuaerp.json.JsonUtils.write(scopedInventory.productOverview(1, 100, subject, text(p, "keyword")));
        }
        if (!headquarters && !java.util.Set.of("shop.list", "order.list", "refund.list", "member.list").contains(resource))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该全局业务资料仅允许总部查询，请使用对应主体工作台");
        var salesScope = java.util.Set.of("shop.list", "order.list", "refund.list", "member.list").contains(resource)
                ? access.salesScope(userId) : null;
        return switch (resource) {
            case "shop.list" -> shopTools.getShopList(salesScope);
            case "warehouse.list" -> warehouseTools.getWarehouseList();
            case "goods.search" -> goodsTools.searchGoods(text(p, "keyword"));
            case "goods.sku" -> goodsTools.searchSku(text(p, "keyword"));
            case "inventory.list" -> throw new IllegalStateException("库存必须走范围查询");
            case "order.list" -> orderTools.getOrderList(days(p), integer(p, "orderStatus"), limit(p, 100), salesScope);
            case "refund.list" -> refundTools.getRefundList(days(p), text(p, "reason"), text(p, "goodsName"), limit(p, 200), salesScope);
            case "purchase.list" -> purchaseTools.getPurchaseOrderList(days(p), integer(p, "status"), limit(p, 100));
            case "logistics.waybill" -> logisticsTools.getWaybillList(days(p), integer(p, "status"), limit(p, 100));
            case "stock.in" -> stockFlowTools.getStockInList(days(p), integer(p, "stockInType"), limit(p, 100));
            case "stock.out" -> stockFlowTools.getStockOutList(days(p), integer(p, "type"), limit(p, 100));
            case "member.list" -> memberTools.getMemberList(text(p, "keyword"), limit(p, 50), salesScope);
            case "supplier.list" -> supplierTools.getSupplierList(text(p, "keyword"), limit(p, 50));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的数据查询：" + resource);
        };
    }

    private static int days(Map<String,Object> p) {
        Integer days=integer(p,"days");
        return days==null ? 30 : Math.max(1,Math.min(90,days));
    }

    private static Integer integer(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) return null;
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) return null;
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询数字参数无效");
        }
    }

    /** limit 统一夹到 [1, MAX_LIMIT]，避免模型传超大值把接口拖慢。 */
    private static Integer limit(Map<String, Object> params, int fallback) {
        Integer value = integer(params, "limit");
        if (value == null || value <= 0) return fallback;
        return Math.min(value, MAX_LIMIT);
    }

    private static String text(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) return null;
        String raw = String.valueOf(value).trim();
        if (raw.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搜索词不能超过100字");
        return raw.isEmpty() ? null : raw;
    }

    private void requireLoopback(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remote) && !"0:0:0:0:0:0:0:1".equals(remote) && !"::1".equals(remote)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅允许本机调用");
        }
    }

    private void requireSecret(String provided) {
        String expected = python.agentSecret();
        if (expected == null || expected.length() < 32
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "密钥校验失败");
        }
    }

    private long verifyToken(String token) {
        try {
            return toolToken.verify(token);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    /** 请求体：查询类型 + 参数表；参数由 Python 侧工具调用生成，这里只做类型收敛。 */
    public record ToolQuery(String resource, Map<String, Object> params) {}
}
