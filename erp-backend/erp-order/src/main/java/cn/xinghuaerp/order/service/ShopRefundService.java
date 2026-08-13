package cn.xinghuaerp.order.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.order.entity.ShopRefund;
import cn.xinghuaerp.order.request.SaleOrderAfterAddRequest;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【oms_shop_refund(视频号小店退款)】的数据库操作Service
* @createDate 2025-07-15 12:25:58
*/
public interface ShopRefundService extends IService<ShopRefund> {
    PageResult<ShopRefund> queryPageList(ShopRefund bo, PageQuery pageQuery);

    ResultVo<Long> saveRefund(Long shopId,ShopRefund bo);

    /**
     * 添加店铺订单售后
     * @param addRequest
     * @return
     */
    ResultVo<Long> addRefund(SaleOrderAfterAddRequest addRequest);
}
