package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiRiskReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/ai-data/risk")
public class AiRiskReadController extends BaseController {
    private final AiAgentPermissionService permissions;
    private final BusinessAccessService access;
    private final JdbcTemplate jdbc;
    private final AiRiskReadService risk;
    @GetMapping("/refunds")
    public AjaxResult refunds(@RequestParam(defaultValue="1") int page) {
        if (!permissions.allows(getUserId(), "ai:risk:query")) throw new AccessDeniedException("无风险资料查询权限");
        var scope=access.scope(getUserId());
        if (page<1 || page>100000) throw new IllegalArgumentException("页码无效");
        String where=" WHERE 1=1"; var args=new ArrayList<Object>();
        switch(scope.type()) {
            case "HEADQUARTERS" -> { }
            case "MERCHANT" -> { if(scope.merchantId()==null || scope.merchantId()<=0) throw new AccessDeniedException("商户范围无效"); where+=" AND merchant_id=?"; args.add(scope.merchantId()); }
            case "SHOP" -> { if(scope.merchantId()==null || scope.merchantId()<0 || scope.shopId()==null || scope.shopId()<=0) throw new AccessDeniedException("店铺范围无效"); where+=" AND merchant_id=? AND shop_id=?"; args.add(scope.merchantId()); args.add(scope.shopId()); }
            default -> throw new AccessDeniedException("当前账号不支持售后资料查询");
        }
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM o_refund"+where,Long.class,args.toArray());
        var queryArgs=new ArrayList<>(args); queryArgs.add(50); queryArgs.add((page-1)*50);
        var rows=jdbc.queryForList("SELECT id,refund_num AS refundNum,order_num AS orderNum,merchant_id AS merchantId,shop_id AS shopId,refund_type AS refundType,goods_name AS goodsName,goods_sku AS goodsSku,quantity,refund_fee AS refundFee,status,erp_status AS erpStatus,create_time AS createTime,update_time AS updateTime FROM o_refund"+where+" ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?",queryArgs.toArray());
        return success(Map.of("total",Objects.requireNonNull(total),"records",rows,"page",page));
    }

    /** 授权范围内的售后记录总览与风险发现项；统计范围由服务端解析，不接受前端指定。 */
    @GetMapping("/overview")
    public AjaxResult overview() {
        if (!permissions.allows(getUserId(), "ai:risk:query")) throw new AccessDeniedException("无风险资料查询权限");
        var scope = access.scope(getUserId());
        if (scope == null || !List.of("HEADQUARTERS", "MERCHANT", "SHOP").contains(scope.type())) throw new AccessDeniedException("当前账号不支持售后资料查询");
        return success(risk.overview(scope));
    }
}
