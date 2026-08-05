package cn.xinghuaerp.purchase.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrder;
import cn.xinghuaerp.purchase.request.PurchaseOrderAddRequest;
import cn.xinghuaerp.purchase.request.PurchaseOrderOptionRequest;
import cn.xinghuaerp.purchase.request.SearchRequest;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【scm_purchase_order(采购订单)】的数据库操作Service
* @createDate 2024-10-20 15:36:33
*/
public interface ErpPurchaseOrderService extends IService<ErpPurchaseOrder> {
    PageResult<ErpPurchaseOrder> queryPageList(SearchRequest bo, PageQuery pageQuery);
    ErpPurchaseOrder getDetailById(Long id);
    ResultVo<Long> createPurchaseOrder(PurchaseOrderAddRequest addBo);
    int updateScmPurchaseOrder(PurchaseOrderOptionRequest request);
}
