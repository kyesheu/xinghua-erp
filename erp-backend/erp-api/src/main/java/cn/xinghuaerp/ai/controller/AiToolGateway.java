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
 * 因此 Python 服务即使被其他人直接访问，也无法绕过身份取走别人的数据。</p>
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
        String text = execute(query.resource().trim(), params);
        log.info("AI 工具查询 userId={} resource={}", userId, query.resource());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resource", query.resource());
        result.put("text", text == null ? "" : text);
        return result;
    }

    /** 白名单分发：新增工具必须同时改这里与 Python 侧的工具说明，不接受任意方法名。 */
    private String execute(String resource, Map<String, Object> p) {
        return switch (resource) {
            case "shop.list" -> shopTools.getShopList();
            case "warehouse.list" -> warehouseTools.getWarehouseList();
            case "goods.search" -> goodsTools.searchGoods(text(p, "keyword"));
            case "goods.sku" -> goodsTools.searchSku(text(p, "keyword"));
            case "inventory.list" -> inventoryTools.getInventoryList(integer(p, "availableLte"), limit(p, 100));
            case "order.list" -> orderTools.getOrderList(integer(p, "days"), integer(p, "orderStatus"), limit(p, 100));
            case "refund.list" -> refundTools.getRefundList(integer(p, "days"), text(p, "reason"), text(p, "goodsName"), limit(p, 200));
            case "purchase.list" -> purchaseTools.getPurchaseOrderList(integer(p, "days"), integer(p, "status"), limit(p, 100));
            case "logistics.waybill" -> logisticsTools.getWaybillList(integer(p, "days"), integer(p, "status"), limit(p, 100));
            case "stock.in" -> stockFlowTools.getStockInList(integer(p, "days"), integer(p, "stockInType"), limit(p, 100));
            case "stock.out" -> stockFlowTools.getStockOutList(integer(p, "days"), integer(p, "type"), limit(p, 100));
            case "member.list" -> memberTools.getMemberList(text(p, "keyword"), limit(p, 50));
            case "supplier.list" -> supplierTools.getSupplierList(text(p, "keyword"), limit(p, 50));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的数据查询：" + resource);
        };
    }

    private static Integer integer(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) return null;
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
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
