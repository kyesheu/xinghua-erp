package cn.xinghuaerp.order.service;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.order.request.SaleOrderAfterAddRequest;
import cn.xinghuaerp.order.entity.ORefund;
import cn.xinghuaerp.order.bo.RefundProcessingBo;
import cn.xinghuaerp.order.bo.RefundSearchBo;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import java.util.List;


/**
* @author qilip
* @description 针对表【o_refund(退换货表)】的数据库操作Service
* @createDate 2024-03-10 16:23:12
*/
public interface ORefundService extends IService<ORefund> {
    ResultVo<Integer> jdRefundMessage(String serviceId, JsonNode refundDetail);
    ResultVo<Integer> jdvcRefundMessage(String returnId,JsonNode refundDetail);
    ResultVo<Integer> taoRefundMessage(String refundId,JsonNode refundDetail);
    ResultVo<Integer> pddRefundMessage(String refundId,JsonNode refundDetail);
    ResultVo<Integer> douRefundMessage(String refundId,JsonNode refundDetail);
    ResultVo<Integer> weiRefundMessage(String refundId,JsonNode refundDetail);

    ResultVo<Long> shopRefundMessage(Long shopRefundId);
    List<ORefund> selectList(ORefund refund);

    PageResult<ORefund> queryPageList(RefundSearchBo bo, PageQuery pageQuery);

    /**
     * 获取待推送到ERP的退款列表
     * @param pageQuery
     * @return
     */
    PageResult<ORefund> queryPageWaitPushErpList( PageQuery pageQuery);
    ORefund selectById(Long id);

    /**
     * 售后处理
     * @param processingBo
     * @return
     */
    ResultVo<Long> refundProcessing(RefundProcessingBo processingBo, String createBy);

    /**
     * 添加销售订单售后
     * @param addRequest
     * @return
     */
    ResultVo<Long> addSaleOrderAfter(SaleOrderAfterAddRequest addRequest);
}
