package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.product.entity.ErpSupplierProductItem;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface ErpSupplierProductItemFacade {
    PageResult<ErpSupplierProductItem> queryPageList(ErpSupplierProductItem item, PageQuery pageQuery);
    ErpSupplierProductItem getById(java.io.Serializable id);
    boolean removeById(java.io.Serializable id);
    boolean updateById(ErpSupplierProductItem entity);
}
