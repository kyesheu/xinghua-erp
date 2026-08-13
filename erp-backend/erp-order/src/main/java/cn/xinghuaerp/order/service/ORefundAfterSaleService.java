package cn.xinghuaerp.order.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;

import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.order.request.AfterSalesExchangeConfirmRequest;
import cn.xinghuaerp.order.request.AfterSalesReturnedAndStockInRequest;
import cn.xinghuaerp.order.request.AfterSalesShipAgainConfirmRequest;
import cn.xinghuaerp.order.entity.ORefundAfterSale;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【o_after_sale(OMS售后处理表)】的数据库操作Service
* @createDate 2024-09-15 21:30:30
*/
public interface ORefundAfterSaleService extends IService<ORefundAfterSale> {
    PageResult<ORefundAfterSale> queryPageList(ORefundAfterSale bo, PageQuery pageQuery);

    /**
     * 退货确认收货并入库
     * @return
     */
    ResultVo<Long> returnedConfirmAndStockIn(AfterSalesReturnedAndStockInRequest request, Long userId);

    /**
     * 补发确认
     * @param request
     * @param userId
     * @return
     */
    ResultVo<Long> shipAgainConfirm(AfterSalesShipAgainConfirmRequest request, Long userId);
    ResultVo<Long> exchangeConfirm(AfterSalesExchangeConfirmRequest request, Long userId);
}
