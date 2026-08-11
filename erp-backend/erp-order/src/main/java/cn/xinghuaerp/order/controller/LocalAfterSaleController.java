package cn.xinghuaerp.order.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.order.request.LocalAfterSaleRequest;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.order.service.LocalAfterSaleService;
import cn.xinghuaerp.system.service.SalesScope;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import cn.xinghuaerp.system.service.BusinessAccessService;

@RestController
@AllArgsConstructor
@RequestMapping("/api/erp-api/afterSale/local")
@PreAuthorize("@ss.hasPermi('shop:order:edit')")
public class LocalAfterSaleController extends BaseController {
    private final LocalAfterSaleService service;
    private final cn.xinghuaerp.system.service.BusinessAccessService access;

    @GetMapping("/orderItems")
    public AjaxResult items(@RequestParam Long shopId, @RequestParam String orderNum) {
        return success(service.findItems(shopId, orderNum, scope()));
    }
    @PostMapping("/create")
    public AjaxResult create(@RequestBody LocalAfterSaleRequest request) {
        return AjaxResult.success("售后已创建，请继续处理", service.create(request, scope(), getUsername()));
    }
    @GetMapping("/detail")
    public AjaxResult detail(@RequestParam String refundNum) { return success(service.detail(refundNum, scope())); }
    @PostMapping("/process")
    public AjaxResult process(@RequestBody LocalAfterSaleRequest request) {
        return AjaxResult.success("已生成售后处理记录", service.process(request.getRefundNum(), scope(), getUsername()));
    }
    @PostMapping("/confirm")
    public AjaxResult confirm(@RequestBody LocalAfterSaleRequest request) {
        service.confirm(request, scope(), getUsername());
        return success("已生成仓库任务，请继续实际入库或备货出库");
    }
    @PostMapping("/confirmSupplierReissue")
    public AjaxResult confirmSupplierReissue(@RequestBody LocalAfterSaleRequest request) {
        service.confirmVendorReissue(request, scope(), getUsername()); return success("已登记供应商发货，未推送外部平台");
    }
    @PostMapping("/complete")
    public AjaxResult complete(@RequestBody LocalAfterSaleRequest request) {
        service.complete(request, scope(), getUsername()); return success("售后已办结，此操作不执行实际支付");
    }
    @PostMapping("/cancel")
    public AjaxResult cancel(@RequestBody LocalAfterSaleRequest request) {
        service.cancel(request.getRefundNum(), scope(), getUsername()); return success("售后申请已取消，未改动库存");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException error) { return error(error.getMessage()); }

    private SalesScope scope() { return access.salesScope(getUserId()); }
}
