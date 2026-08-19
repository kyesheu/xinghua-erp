package cn.xinghuaerp.fulfillment.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.order.bo.OrderShipRequest;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.fulfillment.service.ManualShipmentService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/erp-api/ship/order")
public class ManualShipmentController extends BaseController {
    private final ManualShipmentService shipmentService;
    private final BusinessAccessService access;

    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    @PostMapping("/manualShipment")
    public AjaxResult ship(@RequestBody OrderShipRequest request) {
        access.requireHeadquarters(getUserId());
        var result = shipmentService.ship(request, getUsername());
        return result.getCode() == 0 ? AjaxResult.success(result.getData()) : AjaxResult.error(result.getMsg());
    }
}
