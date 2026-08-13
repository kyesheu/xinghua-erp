package cn.xinghuaerp.order.service;

import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.entity.OOrderItem;
import cn.xinghuaerp.order.bo.ErpOrderShipBo;
import cn.xinghuaerp.order.bo.PushToCloudWarehouseOrderBo;
import cn.xinghuaerp.order.vo.SalesDailyVo;
import cn.xinghuaerp.order.vo.WaitShipReportVo;
import cn.xinghuaerp.order.request.OrderSearchRequest;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;



import java.util.List;

/**
* @author qilip
* @description 针对表【o_order(订单表)】的数据库操作Service
* @createDate 2024-03-09 13:15:57
*/
public interface OOrderService extends IService<OOrder> {

    ResultVo<Long> erpOrderMessage(String orderNum);
    ResultVo<Long> shopOrderMessage(Long shopOrderId);
    List<OOrder> getList(OOrder order);
    PageResult<OOrder> queryPageList(OrderSearchRequest bo, PageQuery pageQuery);
    PageResult<OOrder> queryWaitDistOrderPageList(OrderSearchRequest bo, PageQuery pageQuery);

    /**
     * 更新订单状态
     * @param orderNo
     * @param orderStatus
     * @param refundStatus
     * @return
     */
    ResultVo<Long> updateOrderStatus(String orderNo, Integer orderStatus, Integer refundStatus);
    /**
     * 已经发货的list(自己发货)
     * @param bo
     * @param pageQuery
     * @return
     */
    PageResult<OOrder> querySelfShippedPageList(OrderSearchRequest bo, PageQuery pageQuery);

    OOrder queryDetailAndCouponById(Long id);
    OOrder queryDetailById(Long id);

    OOrder queryDetailByOrderNum(String orderNum);
    OOrder queryByOrderNum(String orderNum);

    List<OOrder> searchOrderConsignee(String consignee);
    List<OOrderItem> searchOrderItemByReceiverMobile(String receiverMobile);
    List<OOrderItem> queryItemList(String orderId);

    /**
     * 待发货统计
     * @return
     */
    List<WaitShipReportVo> waitOrderReport(Long merchantId);
    List<SalesDailyVo> salesDaily();
    SalesDailyVo getTodaySalesDaily(Long merchantId);
    Integer getWaitShipOrderAllCount(Long merchantId);
//    ResultVo<Integer> saveWaybillCode(String orderNum,Long shopId,Integer shopType, String WaybillCode);
    /**
     * 手动发货(本地发货)
     * @param shipBo
     * @return 返回备货单id
     */
    ResultVo<Long> localManualShipmentOrder(ErpOrderShipBo shipBo, String createBy);

    /**
     * 推送订单到云仓（京东云仓、系统云仓）
     * @param
     * @param
     * @return
     */
    ResultVo<Long> pushOrderToCloudWarehouse(PushToCloudWarehouseOrderBo bo);

    /**
     * 取消店铺订单
     * @param shopOrderId
     * @return
     */
    ResultVo cancelShopOrder(Long shopOrderId);

    /**
     * 取消抖店订单
     * @param douOrderId
     * @return
     */
    ResultVo cancelDouOrderMessage(String douOrderId);
}
