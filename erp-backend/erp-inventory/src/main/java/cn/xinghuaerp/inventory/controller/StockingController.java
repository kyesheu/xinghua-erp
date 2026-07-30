package cn.xinghuaerp.inventory.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.inventory.bo.ShipStockUpCompleteBo;
import cn.xinghuaerp.inventory.bo.StockingOrderBo;
import cn.xinghuaerp.inventory.bo.StockingOrderItemBo;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.inventory.service.OOrderStockingItemService;
import cn.xinghuaerp.inventory.service.OOrderStockingService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 备货单管理（发货→备货→出库→减库存）
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/ship/stocking")
public class StockingController extends BaseController {

    private final OOrderStockingService stockingService;
    private final OOrderStockingItemService stockingItemService;

    /**
     * 备货清单列表
     */
    @GetMapping("/stock_up_list")
    public TableDataInfo stockUpList(StockingOrderBo bo, PageQuery pageQuery) {
        var pageList = stockingService.queryStockUpPageList(bo, pageQuery);
        return getDataTable(pageList);
    }

    /**
     * 备货商品清单列表
     */
    @GetMapping("/stock_up_item_list")
    public TableDataInfo stockUpItemList(StockingOrderItemBo bo, PageQuery pageQuery) {
        var pageList = stockingItemService.queryStockingPageList(bo, pageQuery);
        return getDataTable(pageList);
    }

    /**
     * 生成出库单（按明细项）
     */
    @PostMapping("/generateStockOutByItem")
    public AjaxResult generateStockOutByItem(@RequestBody ShipStockUpCompleteBo bo) {
        if (bo.getIds() == null || bo.getIds().length == 0) {
            return AjaxResult.error("请选择备货明细");
        }
        var result = stockingItemService.generateStockOutByItem(bo);
        if (result.getCode() == 0) return AjaxResult.success();
        return AjaxResult.error(result.getMsg());
    }

    /**
     * 生成出库单（按订单）
     */
    @PostMapping("/generateStockOutByOrder")
    public AjaxResult generateStockOutByOrder(@RequestBody ShipStockUpCompleteBo bo) {
        if (bo.getStockingId() == null) {
            return AjaxResult.error("缺少参数：备货单ID");
        }
        var result = stockingItemService.generateStockOutByOrder(bo);
        if (result.getCode() == 0) return AjaxResult.success();
        return AjaxResult.error(result.getMsg());
    }
}