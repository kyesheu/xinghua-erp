package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiMarketingReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/ai-data/marketing")
public class AiMarketingReadController extends BaseController {
    private final AiAgentPermissionService permissions;
    private final BusinessAccessService access;
    private final JdbcTemplate jdbc;
    private final AiMarketingReadService marketing;

    @GetMapping("/rules")
    public AjaxResult rules(@RequestParam(defaultValue="1") int page) {
        if (!permissions.allows(getUserId(), "ai:marketing:query")) throw new AccessDeniedException("无营销资料查询权限");
        var scope = access.scope(getUserId());
        if (!List.of("HEADQUARTERS", "MERCHANT", "SHOP").contains(scope.type())) throw new AccessDeniedException("当前账号不支持营销资料查询");
        if (page < 1 || page > 100000) throw new IllegalArgumentException("页码无效");
        String where = " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if ("MERCHANT".equals(scope.type())) {
            where += " AND (apply_scope=1 OR (apply_scope IN (2,3) AND apply_merchant_id=?))";
            args.add(scope.merchantId());
        } else if ("SHOP".equals(scope.type())) {
            where += " AND (apply_scope=1 OR (apply_scope=2 AND apply_merchant_id=?) OR (apply_scope=3 AND apply_merchant_id=? AND apply_shop_id=?))";
            args.add(scope.merchantId()); args.add(scope.merchantId()); args.add(scope.shopId());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM o_marketing_discount_rule" + where, Long.class, args.toArray());
        var queryArgs = new ArrayList<>(args); queryArgs.add(50); queryArgs.add((page-1)*50);
        var rows = jdbc.queryForList("SELECT id, rule_name AS ruleName, discount_type AS discountType, discount_value AS discountValue, apply_scope AS applyScope, apply_merchant_id AS merchantId, apply_shop_id AS shopId, total_quota AS totalQuota, used_quota AS usedQuota, min_order_amount AS minOrderAmount, start_time AS startTime, end_time AS endTime, status, priority, remark FROM o_marketing_discount_rule" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", queryArgs.toArray());
        return success(Map.of("total", Objects.requireNonNull(total), "records", rows, "page", page));
    }

    /** 授权范围内的营销规则总览与风险发现项；统计范围由服务端解析，不接受前端指定。 */
    @GetMapping("/overview")
    public AjaxResult overview() {
        if (!permissions.allows(getUserId(), "ai:marketing:query")) throw new AccessDeniedException("无营销资料查询权限");
        var scope = access.scope(getUserId());
        if (scope == null || !List.of("HEADQUARTERS", "MERCHANT", "SHOP").contains(scope.type())) throw new AccessDeniedException("当前账号不支持营销资料查询");
        return success(marketing.overview(scope));
    }
}
