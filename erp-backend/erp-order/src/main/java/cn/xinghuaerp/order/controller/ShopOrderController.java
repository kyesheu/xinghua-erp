package cn.xinghuaerp.order.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.order.service.ShopPullApiService;
import cn.xinghuaerp.order.entity.ShopOrder;
import cn.xinghuaerp.order.entity.ShopOrderItem;
import cn.xinghuaerp.order.bo.ShopOrderCreateBo;
import cn.xinghuaerp.order.query.ShopOrderQueryBo;
import cn.xinghuaerp.order.request.OrderPullRequest;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.order.service.ShopOrderItemService;
import cn.xinghuaerp.order.service.impl.ShopSyncService;
import cn.xinghuaerp.order.service.ShopOrderService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/oms-api/shop/order")
public class ShopOrderController extends BaseController {

    private final ShopOrderService shopOrderService;
    private final ShopOrderItemService shopOrderItemService;
    private final ShopSyncService shopSyncService;
    private final ShopPullApiService shopPullApiService;

    /**
     * 查询店铺订单列表
     */
    @GetMapping("/list")
    public TableDataInfo list(ShopOrderQueryBo bo, PageQuery pageQuery) {
        PageResult<ShopOrder> pageList = shopOrderService.queryOrderPageList(bo, pageQuery);
        return getDataTable(pageList);
    }

    /**
     * 查询店铺订单明细列表
     */
    @GetMapping("/item_list")
    public TableDataInfo itemList(ShopOrderQueryBo bo, PageQuery pageQuery) {
        PageResult<ShopOrderItem> pageList = shopOrderItemService.queryPageList(bo, pageQuery);
        return getDataTable(pageList);
    }

    /**
     * 手动创建店铺订单（金额单位：元，后端转换为分）。
     * 前端：{@code /api/oms-api/shop/order/create}
     */
    @PostMapping("/create")
    public AjaxResult create(@RequestBody ShopOrderCreateBo bo) {
        if (bo == null || bo.getShopId() == null) {
            return AjaxResult.error("参数错误，缺少店铺信息");
        }
        ResultVo<Long> result = shopOrderService.createOrder(bo, getUsername());
        if (result.getCode() == 0) {
            // createOrder 的事务已完成；同步失败不能把已创建订单报告成创建失败。
            boolean synced = false;
            try {
                ResultVo<Long> sync = shopSyncService.syncOrder(result.getData());
                synced = sync != null && sync.getCode() == 0;
            } catch (Exception e) {
                log.error("手工订单已创建，但同步订单库异常，shopOrderId={}", result.getData(), e);
            }
            AjaxResult response = AjaxResult.success(synced ? "订单已创建并同步到订单库"
                    : "订单已创建，但同步到订单库失败。请勿重复创建，可在店铺订单中选中该订单，点击“重新推送选中订单到订单库”重试。", result.getData());
            response.put("omsSynced", synced);
            return response;
        }
        return AjaxResult.error(result.getCode(), result.getMsg());
    }

    /**
     * 拉取店铺订单列表（按下单日期，根据 shopType 路由到对应平台 SDK）。
     * 前端：{@code /api/oms-api/shop/order/pull_list}，参数 {shopId, startTime, endTime}
     */
    @PostMapping("/pull_list")
    public AjaxResult pullList(@RequestBody OrderPullRequest req) {
        ResultVo<String> result = shopPullApiService.pullOrder(req.getShopId(), req.getStartTime(), req.getEndTime());
        if (result.getCode() == 0) {
            return AjaxResult.success(result.getMsg());
        }
        return AjaxResult.error(result.getCode(), result.getMsg());
    }

    /**
     * 拉取单个订单详情并同步。
     * 前端：{@code /api/oms-api/shop/order/pull_detail}，参数 {shopId, orderId}
     */
    @PostMapping("/pull_detail")
    public AjaxResult pullDetail(@RequestBody OrderPullRequest req) {
        ResultVo<String> result = shopPullApiService.pullOrderDetail(req.getShopId(), req.getOrderId());
        if (result.getCode() == 0) {
            return AjaxResult.success(result.getMsg());
        }
        return AjaxResult.error(result.getCode(), result.getMsg());
    }

    /**
     * 推送到 OMS（同步 oms_shop_order → o_order）。
     * 前端：{@code /api/oms-api/shop/order/push_oms}，参数 {ids:[shopOrderId...]}
     */
    @PostMapping("/push_oms")
    public AjaxResult pushOms(@RequestBody Map<String, Object> body) {
        Object idsObj = body.get("ids");
        if (idsObj == null) {
            return AjaxResult.error("参数错误，缺少ids");
        }
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) idsObj;
        int success = 0, fail = 0;
        for (Object id : ids) {
            try {
                Long shopOrderId = Long.parseLong(String.valueOf(id));
                ResultVo<Long> r = shopSyncService.syncOrder(shopOrderId);
                if (r.getCode() == 0) success++;
                else fail++;
            } catch (Exception e) {
                log.error("推送订单到OMS异常：{}", id, e);
                fail++;
            }
        }
        AjaxResult response = AjaxResult.success("同步完成，成功：" + success + "条，失败：" + fail + "条");
        response.put("successCount", success);
        response.put("failCount", fail);
        return response;
    }
}
