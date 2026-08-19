package cn.xinghuaerp.fulfillment.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.fulfillment.service.ManualShipmentService;
import cn.xinghuaerp.order.bo.OrderShipRequest;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.system.service.BusinessAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 订单发货入口：订单确认后由发货域执行出库与面单打印。 */
@RestController
@RequestMapping("/api/erp-api/order")
@RequiredArgsConstructor
public class OrderShipmentController extends BaseController {

    private final ManualShipmentService manualShipmentService;
    private final BusinessAccessService access;

    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    @PostMapping("/manualShipment")
    public AjaxResult manualShipment(@RequestBody OrderShipRequest shipBo) {
        access.requireHeadquarters(getUserId());
        var result = manualShipmentService.ship(shipBo, getUsername());
        return result.getCode() == 0 ? AjaxResult.success(result.getData()) : AjaxResult.error(result.getMsg());
    }
}
