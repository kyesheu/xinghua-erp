package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiPurchaseDraftService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/ai-agent/inventory")
public class InventoryPurchaseDraftController extends BaseController {
    private final PurchaseAccessService access;
    private final AiAgentPermissionService permissions;
    private final AiPurchaseDraftService drafts;
    public record Request(String requestKey, Long skuId, Long supplierId, Long quantity, BigDecimal unitPrice, Boolean confirmed) {}
    public record Response(String orderId, boolean replayed) {}

    @GetMapping("/purchase-drafts")
    public AjaxResult recent() {
        Long userId = getUserId();
        access.requireHeadquarters(userId, "purchase:order:query");
        if (!permissions.allows(userId, "ai:inventory:query")) throw new AccessDeniedException("无 AI 库存查询权限");
        return success(drafts.recent(userId));
    }

    @GetMapping("/purchase-suppliers")
    public AjaxResult suppliers(@RequestParam Long skuId) {
        Long userId = getUserId();
        access.requireHeadquarters(userId, "purchase:order:create");
        if (!permissions.allows(userId, "ai:inventory:query")) throw new AccessDeniedException("无 AI 库存查询权限");
        return success(drafts.suppliers(skuId));
    }

    @PostMapping("/purchase-draft")
    public AjaxResult create(@RequestBody Request request) {
        Long userId = getUserId();
        access.requireHeadquarters(userId, "purchase:order:create");
        if (!permissions.allows(userId, "ai:inventory:query")) throw new AccessDeniedException("无 AI 库存查询权限");
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) throw new IllegalArgumentException("请确认采购草稿内容");
        var result = drafts.create(new AiPurchaseDraftService.Request(request.requestKey(), request.skuId(), request.supplierId(),
                request.quantity(), request.unitPrice()), userId, getUsername());
        return success(new Response(String.valueOf(result.orderId()), result.replayed()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException e) { return error(e.getMessage()); }
}
