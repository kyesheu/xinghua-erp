package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.security.common.BaseController;
import org.springframework.security.access.AccessDeniedException;
import cn.xinghuaerp.ai.service.AiInventoryReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 供已登录用户查询自身业务范围内的库存。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/ai-data/inventory")
public class AiInventoryReadController extends BaseController {
    private final AiInventoryReadService inventory;
    private final BusinessAccessService access;
    private final AiAgentPermissionService permissions;

    @GetMapping("/overview")
    public AjaxResult overview(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int size) {
        if (!permissions.allows(getUserId(), "ai:inventory:query")) throw new AccessDeniedException("无 AI 库存查询权限");
        return success(inventory.overview(page, size, access.scope(getUserId()), permissions.allows(getUserId(), "ai:sales:query"), permissions.allows(getUserId(), "ai:purchase:query")));
    }

    @GetMapping
    public AjaxResult find(@RequestParam Long skuId, @RequestParam(required = false) Long warehouseId) {
        if (!permissions.allows(getUserId(), "ai:inventory:query"))
            throw new AccessDeniedException("无 AI 库存查询权限");
        return success(inventory.find(skuId, warehouseId, access.scope(getUserId())));
    }
    @GetMapping("/dossier")
    public AjaxResult dossier(@RequestParam Long skuId) {
        Long userId = getUserId();
        if (!permissions.allows(userId, "ai:inventory:query")) throw new AccessDeniedException("无 AI 库存查询权限");
        return success(inventory.dossier(skuId, access.scope(userId), permissions.allows(userId, "ai:sales:query"), permissions.allows(userId, "ai:purchase:query")));
    }
    @GetMapping("/search")
    public AjaxResult search(@RequestParam String keyword) {
        if (!permissions.allows(getUserId(), "ai:inventory:query")) throw new AccessDeniedException("无 AI 库存查询权限");
        return success(inventory.search(keyword, access.scope(getUserId())));
    }
    @GetMapping("/sales")
    public AjaxResult sales(@RequestParam Long skuId, @RequestParam(defaultValue = "30") int days) {
        if (!permissions.allows(getUserId(), "ai:sales:query"))
            throw new AccessDeniedException("无 AI 销售查询权限");
        return success(inventory.sales(skuId, days, access.scope(getUserId())));
    }
    @GetMapping("/purchase-inbound")
    public AjaxResult purchaseInbound(@RequestParam Long skuId, @RequestParam(required = false) Long warehouseId) {
        if (!permissions.allows(getUserId(), "ai:purchase:query"))
            throw new AccessDeniedException("无 AI 采购查询权限");
        return success(inventory.purchaseInbound(skuId, warehouseId, access.scope(getUserId())));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException error) { return error(error.getMessage()); }
}
