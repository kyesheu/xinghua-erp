package cn.xinghuaerp.finance.controller;
import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.finance.service.SettlementReconciliationService;
import cn.xinghuaerp.system.service.*;
import cn.xinghuaerp.system.security.common.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/fms/settlement-reconciliation")
public class SettlementReconciliationController extends BaseController {
 private final SettlementReconciliationService reconciliation;
 private final BusinessAccessService access;
 private final AiAgentPermissionService permissions;
 @GetMapping public AjaxResult inspect(@RequestParam(defaultValue="0") long afterId,@RequestParam(defaultValue="100") int size) {
  Long user=getUserId();if(!permissions.allows(user,"ai:finance:query"))throw new org.springframework.security.access.AccessDeniedException("无财务对账查询权限");
  return success(reconciliation.inspect(access.scope(user),afterId,size));
 }
}
