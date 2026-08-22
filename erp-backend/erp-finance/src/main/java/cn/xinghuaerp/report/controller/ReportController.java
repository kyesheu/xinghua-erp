package cn.xinghuaerp.report.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.BusinessAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.xinghuaerp.order.vo.SalesDailyVo;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.order.service.OOrderItemService;
import cn.xinghuaerp.order.service.OOrderService;
import cn.xinghuaerp.system.service.OShopService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/report")
public class ReportController extends BaseController {
    private final OOrderService orderService;
    private final OOrderItemService orderItemService;
    private final OShopService shopService;
    private final BusinessAccessService access;

    @PreAuthorize("@ss.hasPermi('shop:order:list')")
    @GetMapping("/todayDaily")
    public AjaxResult todayDaily()
    {
        access.requireHeadquarters(getUserId());
        Long shopCount = shopService.list().stream().count();
        Map<String,Object> result = new HashMap<>();
        // 今日销售
        SalesDailyVo todaySalesDaily = orderService.getTodaySalesDaily(0L);
        // 查询库存
//        Long allInventoryQuantity = inventoryService.getAllInventoryQuantity();
//        result.put("inventory",allInventoryQuantity.doubleValue());
        result.put("waitShip",orderService.getWaitShipOrderAllCount(0L));
        result.put("salesVolume",todaySalesDaily.getAmount());
        result.put("orderCount",todaySalesDaily.getCount().doubleValue());
        result.put("shopCount",shopCount.doubleValue());

        return AjaxResult.success(result);
    }


    @PreAuthorize("@ss.hasPermi('shop:order:list')")
    @GetMapping("/salesDaily")
    public AjaxResult salesDaily()
    {
        access.requireHeadquarters(getUserId());
        List<SalesDailyVo> salesDailyVos = orderService.salesDaily();

        return AjaxResult.success(salesDailyVos);
    }

}
