package cn.xinghuaerp.order.controller;

import cn.xinghuaerp.order.request.OrderCancelRequest;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import cn.xinghuaerp.order.entity.OOrder;
import org.springframework.security.access.AccessDeniedException;
import java.util.Objects;
import cn.xinghuaerp.common.*;
import cn.xinghuaerp.order.bo.OrderAllocateShipRequest;
import cn.xinghuaerp.order.request.OrderSearchRequest;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.order.service.OOrderItemService;
import cn.xinghuaerp.order.service.OOrderService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.TableDataInfo;

/**
 * 店铺订单Controller
 *
 * @author xinghua
 * @date 2023-12-31
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/order")
public class OrderController extends BaseController
{

    private final OOrderService orderService;
    private final OOrderItemService orderItemService;
    private final BusinessAccessService access;

    static void requireSalesScope(Scope scope) {
        if (scope == null || !("HEADQUARTERS".equals(scope.type()) ||
                ("MERCHANT".equals(scope.type()) && scope.merchantId() != null) ||
                ("SHOP".equals(scope.type()) && scope.merchantId() != null && scope.shopId() != null)))
            throw new AccessDeniedException("当前账号没有订单访问范围");
    }

    static void applyOrderScope(OrderSearchRequest request, Scope scope) {
        requireSalesScope(scope);
        if (!"HEADQUARTERS".equals(scope.type())) {
            request.setMerchantId(scope.merchantId());
            if ("SHOP".equals(scope.type())) request.setShopId(scope.shopId());
        }
    }

    static void checkOrderScope(OOrder order, Scope scope) {
        if (order == null || "HEADQUARTERS".equals(scope.type())) return;
        if (!Objects.equals(scope.merchantId(), order.getMerchantId()) ||
                ("SHOP".equals(scope.type()) && !Objects.equals(scope.shopId(), order.getShopId())))
            throw new AccessDeniedException("无权访问该订单");
    }

    /**
     * 查询店铺订单列表
     */
    @PreAuthorize("@ss.hasPermi('shop:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(OrderSearchRequest bo, PageQuery pageQuery)
    {
        applyOrderScope(bo, access.scope(getUserId()));
        var pageList = orderService.queryPageList(bo,pageQuery);
        return getDataTable(pageList);
    }


    /**
     * 获取店铺订单详细信息
     */
    @PreAuthorize("@ss.hasPermi('shop:order:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        var scope = access.scope(getUserId());
        requireSalesScope(scope);
        var order = orderService.queryDetailById(id);
        checkOrderScope(order, scope);
        return success(order);
    }
    /**
     * 待发货列表（去除处理过的）
     * @param order
     * @param pageQuery
     * @return
     */
    @GetMapping("/waitShipmentList")
    public TableDataInfo waitShipmentList(OrderSearchRequest order, PageQuery pageQuery)
    {
//        var pageList = orderService.queryWaitShipmentPageList(order,pageQuery);
//        return getDataTable(pageList);
        return getDataTable(new ArrayList<>());
    }

    /**
     * 待分配发货订单list（部分分配）
     * @param bo
     * @param pageQuery
     * @return
     */
    @PreAuthorize("@ss.hasPermi('shop:order:list')")
    @GetMapping("/wait_dist_order_list")
    public TableDataInfo wait_send_orderlist(OrderSearchRequest bo, PageQuery pageQuery) {

        applyOrderScope(bo, access.scope(getUserId()));
        var pageList = orderService.queryWaitDistOrderPageList(bo,pageQuery);
        return getDataTable(pageList);
    }

    /**
     * 待分配发货订单item list
     * @param bo
     * @param pageQuery
     * @return
     */
    @PreAuthorize("@ss.hasPermi('shop:order:list')")
    @GetMapping("/wait_dist_order_item_list")
    public TableDataInfo wait_send_orderItemList(OrderSearchRequest bo, PageQuery pageQuery) {
        applyOrderScope(bo, access.scope(getUserId()));
        var pageList = orderItemService.queryWaitDistOrderItemPageList(bo,pageQuery);
        return getDataTable(pageList);
    }

//    @PostMapping
//    public AjaxResult add(@RequestBody OrderCreateBo order)
//    {
//        if(order.getGoodsAmount()==null)return new AjaxResult(1503,"请填写商品价格！");
//
//        int result = orderService.insertErpOrder(order,getUsername());
//        if(result == -1) return new AjaxResult(501,"订单号已存在！");
//        if(result == -2) return new AjaxResult(502,"请添加订单商品！");
//        if(result == -3) return new AjaxResult(503,"请完善订单商品明细！");
//        if(result == -4) return new AjaxResult(504,"请选择店铺！");
//        return toAjax(result);
//    }
//    /**
//     * 订单发货
//     * @param order
//     * @return
//     */
//    @Log(title = "店铺订单", businessType = BusinessType.UPDATE)
//    @PostMapping("/ship")
//    public AjaxResult ship(@RequestBody ErpOrder order)
//    {
//        order.setUpdateBy(getUsername());
//        int result = orderService.shipErpOrder(order);
//        if(result == -1) return new AjaxResult(501,"订单不存在！");
//        else if(result == -2) return new AjaxResult(502,"订单号已存在！");
//        return toAjax(result);
//    }

    /**
     * 分配供应商发货
     * @param shipBo
     * @return
     */
    @PostMapping("/allocateShipmentOrder")
    public AjaxResult allocateShipmentOrder(@RequestBody OrderAllocateShipRequest shipBo)
    {
        if (!StringUtils.hasLength(shipBo.getId()) || shipBo.getId().equals("0"))
            return AjaxResult.error("缺少参数：id");
        if(shipBo.getSupplierId() == null || shipBo.getSupplierId()<=0)
            return AjaxResult.error("缺少参数：supplierId");
//        var result = orderService.allocateShipmentOrder(shipBo,getUsername());
//        if(result.getCode() == 0) return AjaxResult.success();
//        else return AjaxResult.error(result.getMsg());
        return AjaxResult.error("升级中");
    }
    /**
     * 取消订单（如果有itemId就是取消子订单）
     * @param request
     * @return
     */
    @PostMapping("/cancelOrder")
    public AjaxResult cancelOrder(@RequestBody OrderCancelRequest request) {
        if (request.getId() == null) return AjaxResult.error("确实参数：Id");
        if (!StringUtils.hasLength(request.getCancelReason())) return AjaxResult.error("请填写取消原因");
//        if (request.getOrderItemId() != null && request.getOrderItemId() > 0) {
//            log.info("=====取消子订单====={}", JSONObject.toJSONString(request));
//            var result = orderService.cancelOrderItem(request.getOrderItemId(), request.getCancelReason(), getUsername());
//            if (result.getCode() == 0) {
//                log.info("==============子订单取消成功=========");
//                return AjaxResult.success();
//            } else return AjaxResult.error(result.getMsg());
//        } else {
//            log.info("======取消订单======{}", JSONObject.toJSONString(request));
//            var result = orderService.cancelOrder(request.getId(), request.getCancelReason(), getUsername());
//            if (result.getCode() == 0) {
//                log.info("==============订单取消成功=========");
//                return AjaxResult.success();
//            } else return AjaxResult.error(result.getMsg());
//        }
        return AjaxResult.error("升级中");
    }
}
