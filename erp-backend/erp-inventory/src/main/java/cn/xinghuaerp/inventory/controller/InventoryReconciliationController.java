package cn.xinghuaerp.inventory.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.inventory.service.InventoryReconciliationService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.security.common.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/inventory-reconciliation")
public class InventoryReconciliationController extends BaseController {
    private final InventoryReconciliationService reconciliation;
    private final BusinessAccessService access;
    private final AiAgentPermissionService permissions;
    @GetMapping
    public AjaxResult inspect(@RequestParam(defaultValue="0") long afterId,@RequestParam(defaultValue="100") int size) {
        Long userId=getUserId();
        if(!permissions.allows(userId,"wms:inventory:query"))throw new AccessDeniedException("无库存对账查询权限");
        return success(reconciliation.inspect(access.scope(userId),afterId,size));
    }
    @GetMapping("/orphan-batches")
    public AjaxResult orphanBatches(@RequestParam(defaultValue="0") long afterId,@RequestParam(defaultValue="100") int size) {
        Long userId=getUserId();
        if(!permissions.allows(userId,"wms:inventory:query"))throw new AccessDeniedException("无批次对账查询权限");
        return success(reconciliation.orphanBatches(access.scope(userId),afterId,size));
    }
}
