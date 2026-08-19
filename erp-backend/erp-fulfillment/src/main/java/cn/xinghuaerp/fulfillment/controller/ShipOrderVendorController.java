package cn.xinghuaerp.fulfillment.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.purchase.service.SupplierDispatchService;
import cn.xinghuaerp.purchase.request.SupplierDispatchRequest;
import cn.xinghuaerp.purchase.request.SupplierDispatchConfirmRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供应商发货Controller - 分配订单到供应商发货
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/ship/vendor")
public class ShipOrderVendorController extends BaseController {

    private final SupplierDispatchService dispatchService;
    private final BusinessAccessService access;

    /**
     * 推送订单到供应商发货（按订单维度）
     */
    @PostMapping("/pushOrderToSupplier")
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    public AjaxResult pushOrderToSupplier(@RequestBody SupplierDispatchRequest request) {
        access.requireHeadquarters(getUserId());
        return AjaxResult.success(dispatchService.allocate(request, getUsername()));
    }

    @PostMapping("/supplier_ship_confirm")
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    public AjaxResult confirm(@RequestBody SupplierDispatchConfirmRequest request) {
        access.requireHeadquarters(getUserId());
        dispatchService.confirm(request, getUsername());
        return AjaxResult.success("已登记供应商发货，未回传外部平台");
    }

    @PostMapping("/supplier_ship_cancel")
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    public AjaxResult cancel(@RequestBody SupplierDispatchConfirmRequest request) {
        access.requireHeadquarters(getUserId());
        dispatchService.cancel(request == null ? null : request.getId(), getUsername());
        return AjaxResult.success("代发任务已撤销");
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException error) { return AjaxResult.error(error.getMessage()); }
}
