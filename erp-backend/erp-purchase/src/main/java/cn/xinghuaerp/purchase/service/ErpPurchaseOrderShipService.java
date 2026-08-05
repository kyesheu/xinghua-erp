package cn.xinghuaerp.purchase.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrderShip;
import cn.xinghuaerp.purchase.request.PurchaseOrderStockInBo;
import cn.xinghuaerp.purchase.request.SearchRequest;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【scm_purchase_order_ship(采购订单物流表)】的数据库操作Service
* @createDate 2024-10-20 17:18:53
*/
public interface ErpPurchaseOrderShipService extends IService<ErpPurchaseOrderShip> {
    PageResult<ErpPurchaseOrderShip> queryPageList(SearchRequest bo, PageQuery pageQuery);

    int updateScmPurchaseOrderShip(ErpPurchaseOrderShip erpPurchaseOrderShip);

    ResultVo<Long> createStockInEntry(PurchaseOrderStockInBo bo,Long userId,String userName);
}
