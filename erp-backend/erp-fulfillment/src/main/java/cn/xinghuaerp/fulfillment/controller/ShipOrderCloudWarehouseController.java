package cn.xinghuaerp.fulfillment.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.BusinessAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.inventory.facade.OOrderStockingWorkflow;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 云仓发货Controller - 推送订单到云仓（保存到备货表）
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/ship/cloudWarehouse")
public class ShipOrderCloudWarehouseController extends BaseController {

    private final OOrderStockingWorkflow stockingService;
    private final BusinessAccessService access;

    /**
     * 推送订单到云仓（按订单维度）
     */
    @PostMapping("/pushToCloudWarehouse")
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    public AjaxResult pushToCloudWarehouse(@RequestBody Map<String, List<Long>> request) {
        access.requireHeadquarters(getUserId());
        return AjaxResult.error("云仓接口尚未接通，未推送订单");
    }
}
