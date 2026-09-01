package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiInventoryReadService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/ai-data/product")
public class AiProductReadController extends BaseController {
    private final AiAgentPermissionService permissions;
    private final BusinessAccessService access;
    private final AiInventoryReadService inventory;
    private final JdbcTemplate jdbc;
    private final PythonAgentClient python;

    private Scope authorized() {
        if (!permissions.allows(getUserId(), "ai:product:query")) throw new AccessDeniedException("无商品运营资料查询权限");
        Scope scope = access.scope(getUserId());
        if (!List.of("HEADQUARTERS", "MERCHANT", "SHOP").contains(scope.type())) throw new IllegalArgumentException("当前账号不支持商品运营资料查询");
        return scope;
    }
    @GetMapping("/overview")
    public AjaxResult overview(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="50") int size, @RequestParam(required=false) String keyword) {
        return success(inventory.productOverview(page, size, authorized(), keyword));
    }
    @GetMapping("/dossier")
    @Transactional(readOnly = true)
    public AjaxResult dossier(@RequestParam Long skuId) {
        Scope scope = authorized();
        if (skuId == null || skuId <= 0) throw new IllegalArgumentException("SKU 参数无效");
        String visible = "SELECT COUNT(*) FROM o_goods_sku s WHERE s.id=?";
        List<Object> args = new ArrayList<>(); args.add(skuId);
        if ("MERCHANT".equals(scope.type())) {
            visible += " AND (s.merchant_id=? OR EXISTS (SELECT 1 FROM o_goods_inventory v WHERE v.sku_id=s.id AND COALESCE(v.is_delete,0)=0 AND v.merchant_id=?))";
            args.add(scope.merchantId()); args.add(scope.merchantId());
        } else if ("SHOP".equals(scope.type())) {
            visible += " AND ((s.merchant_id=? AND s.shop_id=?) OR EXISTS (SELECT 1 FROM o_goods_inventory v WHERE v.sku_id=s.id AND COALESCE(v.is_delete,0)=0 AND v.merchant_id=? AND v.shop_id=?))";
            args.add(scope.merchantId()); args.add(scope.shopId()); args.add(scope.merchantId()); args.add(scope.shopId());
        }
        if (Objects.requireNonNull(jdbc.queryForObject(visible, Long.class, args.toArray())) == 0) throw new AccessDeniedException("商品不在可访问范围内");
        String shops = "SELECT g.id, g.shop_id AS shopId, s.name AS shopName, g.product_id AS productId, g.product_title AS productTitle, g.sku_id AS platformSkuId, g.sku_name AS skuName, g.status, g.update_on AS updateOn FROM oms_shop_goods_sku g LEFT JOIN o_shop s ON s.id=g.shop_id WHERE g.erp_goods_sku_id=?";
        List<Object> shopArgs = new ArrayList<>(); shopArgs.add(skuId);
        if (!"HEADQUARTERS".equals(scope.type())) { shops += " AND g.merchant_id=?"; shopArgs.add(scope.merchantId()); }
        if ("SHOP".equals(scope.type())) { shops += " AND g.shop_id=?"; shopArgs.add(scope.shopId()); }
        shops += " ORDER BY g.id LIMIT 201";
        List<Map<String,Object>> shopRows = jdbc.queryForList(shops, shopArgs.toArray());
        List<Map<String,Object>> supplierRows = List.of();
        boolean suppliersVisible = !"SHOP".equals(scope.type());
        if (suppliersVisible) {
            String sql = "SELECT i.id, p.supplier_id AS supplierId, s.name AS supplierName, p.product_name AS productName, i.sku_code AS skuCode, i.standard, i.status FROM erp_supplier_product_item i JOIN erp_supplier_product p ON p.id=i.supplier_product_id AND p.supplier_id=i.supplier_id LEFT JOIN erp_supplier s ON s.id=p.supplier_id WHERE i.erp_goods_sku_id=?";
            List<Object> supplierArgs = new ArrayList<>(); supplierArgs.add(skuId);
            if ("MERCHANT".equals(scope.type())) { sql += " AND p.merchant_id=?"; supplierArgs.add(scope.merchantId()); }
            supplierRows = jdbc.queryForList(sql + " ORDER BY i.id LIMIT 201", supplierArgs.toArray());
        }
        return success(Map.of("inventory", inventory.find(skuId, null, scope), "suppliersVisible", suppliersVisible,
                "suppliersTruncated", supplierRows.size()>200, "suppliers", supplierRows.subList(0, Math.min(200,supplierRows.size())),
                "shopsTruncated", shopRows.size()>200, "shops", shopRows.subList(0, Math.min(200,shopRows.size()))));
    }
    public record DraftRequest(Long skuId, String language, String audience, String site, String confirmedKeywords) {
        public DraftRequest(Long skuId, String language, String audience) { this(skuId,language,audience,null,null); }
    }
    @PostMapping("/draft")
    public AjaxResult draft(@RequestBody DraftRequest request) {
        if (request == null || request.skuId() == null || request.skuId() <= 0) throw new IllegalArgumentException("请选择商品");
        String language = request.language() == null ? "" : request.language().trim();
        String audience = request.audience() == null ? "" : request.audience().trim();
        String site = request.site() == null ? "" : request.site().trim();
        String keywords = request.confirmedKeywords() == null ? "" : request.confirmedKeywords().trim();
        if (language.isEmpty() || language.length() > 40 || audience.length() > 200 || site.length() > 100 || keywords.length() > 500) throw new IllegalArgumentException("语言、受众、站点或关键词不符合长度要求");
        dossier(request.skuId()); // 重新校验权限和 SKU 范围，客户端不能自行提供商品事实。
        var rows = jdbc.queryForList("SELECT goods_name AS goodsName, sku_code AS skuCode, sku_name AS skuName FROM o_goods_sku WHERE id=?", request.skuId());
        if (rows.isEmpty()) throw new IllegalArgumentException("商品不存在");
        var row = rows.get(0);
        return success(python.productDraft(Map.of("goodsName", Objects.toString(row.get("goodsName"), ""), "skuCode", Objects.toString(row.get("skuCode"), ""), "skuName", Objects.toString(row.get("skuName"), ""), "language", language, "audience", audience, "site",site,"confirmedKeywords",keywords)));
    }
    @ExceptionHandler(IllegalStateException.class)
    public AjaxResult unavailable(IllegalStateException e) { return error(e.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException e) { return error(e.getMessage()); }
}

