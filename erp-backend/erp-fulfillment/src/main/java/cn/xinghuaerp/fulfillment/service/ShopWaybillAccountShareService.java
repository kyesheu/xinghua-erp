package cn.xinghuaerp.fulfillment.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.fulfillment.entity.ShopWaybillAccountShare;

 import cn.xinghuaerp.common.PageQuery;
 import cn.xinghuaerp.common.PageResult;
 import cn.xinghuaerp.common.ResultVo;
 import cn.xinghuaerp.fulfillment.entity.ShopWaybillAccountShare;
 import com.baomidou.mybatisplus.extension.service.IService;

 import java.util.List;

 /**
 * @author qilip
 * @description 店铺电子面单共享service
 * @createDate 2025-06-15 12:25:35
 */
 public interface ShopWaybillAccountShareService extends IService<ShopWaybillAccountShare> {
     /**
      * 获取供应商电子面单账户
      * @param bo
      * @param pageQuery
      * @return
      */
     PageResult<ShopWaybillAccountShare> queryVendorPageList(ShopWaybillAccountShare bo, PageQuery pageQuery);

     /**
      * 获取店面电子面单list
      * @param shipperId
      * @param shopId
      * @return
      */
     List<ShopWaybillAccountShare> queryVendorShopWaybillAccountList(Long shipperId, Long shopId);

     /**
      * 获取共享给发货人的电子面单账户
      * @param bo
      * @param pageQuery
      * @return
      */
     PageResult<ShopWaybillAccountShare> queryShareVendorPageList(ShopWaybillAccountShare bo, PageQuery pageQuery);
     ResultVo<Integer> syncAccountList(Long shopId, List<ShopWaybillAccountShare> accountList);

     /**
      * 电子面单账户共享给发货人
      * @param accountId
      * @param shipperType
      * @param shipperId
      * @return
      */
     ResultVo shareShipper(Long accountId,Integer shipperType,Long shipperId,String shipperName);
 }
