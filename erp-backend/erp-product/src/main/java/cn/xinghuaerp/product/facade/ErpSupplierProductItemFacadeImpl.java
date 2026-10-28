package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.product.entity.ErpSupplierProductItem;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ErpSupplierProductItemFacadeImpl implements ErpSupplierProductItemFacade {
    private final cn.xinghuaerp.product.service.ErpSupplierProductItemService delegate;
    @Override public PageResult<ErpSupplierProductItem> queryPageList(ErpSupplierProductItem item, PageQuery pageQuery) { return delegate.queryPageList(item, pageQuery); }
    @Override public ErpSupplierProductItem getById(java.io.Serializable id) { return delegate.getById(id); }
    @Override public boolean removeById(java.io.Serializable id) { return delegate.removeById(id); }
    @Override public boolean updateById(ErpSupplierProductItem entity) { return delegate.updateById(entity); }
}
