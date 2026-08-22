package cn.xinghuaerp.finance.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.finance.service.FmsOrderSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单结算执行入口（此前结算服务无任何 HTTP 入口，费用与结算功能不可用）。
 *
 * <p>写操作：仅对显式提供的订单 ID 执行既有 {@link FmsOrderSettlementService#autoSettlement}，
 * 结算计算（出库批次成本、发货费用、已审核费用单）全部由结算服务完成，本控制器不做任何金额计算。
 * 需要 {@code fms:settlement:execute} 权限（登记脚本 docs/sql/ai-finance-settlement.sql，不自动授权角色）。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/fms/settlement")
public class FmsSettlementController extends BaseController {
    private final FmsOrderSettlementService settlementService;
    private final AiAgentPermissionService permissions;

    public record Request(List<String> orderIds) {}

    @PostMapping("/auto-run")
    public AjaxResult autoRun(@RequestBody Request request) {
        Long userId = getUserId();
        if (!permissions.allows(userId, "fms:settlement:execute")) throw new AccessDeniedException("无结算执行权限");
        if (request == null || request.orderIds() == null || request.orderIds().isEmpty())
            throw new IllegalArgumentException("请提供需要结算的订单ID");
        if (request.orderIds().size() > 200) throw new IllegalArgumentException("单次最多结算200笔订单");
        for (String id : request.orderIds())
            if (id == null || !id.matches("\\d{1,19}")) throw new IllegalArgumentException("订单ID格式无效: " + id);
        return success(settlementService.autoSettlement(request.orderIds(), getUsername()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException error) { return error(error.getMessage()); }
}
