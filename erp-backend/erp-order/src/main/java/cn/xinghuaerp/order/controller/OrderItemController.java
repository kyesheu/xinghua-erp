package cn.xinghuaerp.order.controller;

import cn.xinghuaerp.order.bo.OrderItemSpecIdUpdateBo;
import cn.xinghuaerp.system.service.BusinessAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.order.query.OrderItemQuery;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.order.service.OOrderItemService;
import cn.xinghuaerp.order.service.OOrderService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 店铺订单Controller
 *
 * @author xinghua
 * @date 2023-12-31
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/order")
public class OrderItemController extends BaseController
{
    private final OOrderItemService itemService;
    private final OOrderService orderService;
    private final BusinessAccessService access;

    /**
     * 查询店铺订单列表
     */
    @PreAuthorize("@ss.hasPermi('shop:order:list')")
    @GetMapping("/item_list")
    public TableDataInfo list(OrderItemQuery bo, PageQuery pageQuery)
    {
        var scope = access.scope(getUserId());
        OrderController.requireSalesScope(scope);
        if (!"HEADQUARTERS".equals(scope.type())) {
            bo.setMerchantId(scope.merchantId());
            if ("SHOP".equals(scope.type())) bo.setShopId(scope.shopId());
        }
        var pageList = itemService.selectPageVo(pageQuery,bo);
        return getDataTable(pageList);
    }
    /**
     * 修改订单明细specId
     * @param bo
     * @return
     */
    @PreAuthorize("@ss.hasPermi('shop:order:edit')")
    @PostMapping("/order_item_sku_id_update")
    public AjaxResult orderItemSpecIdUpdate(@RequestBody OrderItemSpecIdUpdateBo bo)
    {
        access.requireHeadquarters(getUserId());
        if(bo.getOrderItemId()==null || bo.getOrderItemId() ==0) return AjaxResult.error("参数错误：orderItemId为空");
        if(bo.getErpGoodsSpecId()==null || bo.getErpGoodsSpecId() ==0) return AjaxResult.error("参数错误：ErpGoodsSpecId为空");

        var result = itemService.updateErpSkuId(bo.getOrderItemId(),bo.getErpGoodsSpecId().toString());
        if (result.getCode() == 0) {
            return AjaxResult.success();
        } else return AjaxResult.error(result.getMsg());

    }

}
