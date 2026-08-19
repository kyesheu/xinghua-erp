package cn.xinghuaerp.fulfillment.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.inventory.service.OOrderStockingService;
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

import java.util.List;
import java.util.Map;

/**
 * 云仓/供应商发货Controller - 推送订单Item到云仓/供应商（保存到备货表）
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/ship/order")
public class ShipOrderItemPushController extends BaseController {

    private final OOrderStockingService stockingService;
    private final SupplierDispatchService dispatchService;
    private final BusinessAccessService access;

    /**
     * 推送订单Item到云仓（按订单Item维度）
     */
    @PostMapping("/push_order_item_to_cloud_warehouse")
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    public AjaxResult pushOrderItemToCloudWarehouse(@RequestBody Map<String, List<Long>> request) {
        access.requireHeadquarters(getUserId());
        return AjaxResult.error("云仓接口尚未接通，未推送订单");
    }

    @PostMapping("/supplier_item_remaining")
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    public AjaxResult supplierItemRemaining(@RequestBody SupplierDispatchRequest request) {
        access.requireHeadquarters(getUserId());
        try { return AjaxResult.success(dispatchService.remainingItems(request == null ? null : request.getIds())); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/allocate_local_items")
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    public AjaxResult allocateLocalItems(@RequestBody SupplierDispatchRequest request) {
        access.requireHeadquarters(getUserId());
        try { return AjaxResult.success(dispatchService.allocateLocalItems(request, getUsername())); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
    @PostMapping("/local_task_confirm")
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    public AjaxResult confirmLocal(@RequestBody SupplierDispatchConfirmRequest request) {
        access.requireHeadquarters(getUserId());
        try { dispatchService.confirmLocal(request, getUsername()); return AjaxResult.success("已登记本地发货，请继续备货出库"); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
    @PostMapping("/local_task_cancel")
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    public AjaxResult cancelLocal(@RequestBody SupplierDispatchConfirmRequest request) {
        access.requireHeadquarters(getUserId());
        try { dispatchService.cancelLocal(request == null ? null : request.getId(), getUsername()); return AjaxResult.success("已撤销本地任务"); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
    /**
     * 推送订单商品到供应商发货（按商品维度）
     */
    @PostMapping("/push_order_item_to_supplier")
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    public AjaxResult pushOrderItemToSupplier(@RequestBody SupplierDispatchRequest request) {
        access.requireHeadquarters(getUserId());
        try { return AjaxResult.success(dispatchService.allocateItems(request, getUsername())); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
}
