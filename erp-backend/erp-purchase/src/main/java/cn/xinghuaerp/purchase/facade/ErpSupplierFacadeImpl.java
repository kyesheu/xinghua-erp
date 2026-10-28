package cn.xinghuaerp.purchase.facade;

import cn.xinghuaerp.purchase.entity.ErpSupplier;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ErpSupplierFacadeImpl implements ErpSupplierFacade {
    private final cn.xinghuaerp.purchase.service.ErpSupplierService delegate;
    @Override public ErpSupplier getById(java.io.Serializable id) { return delegate.getById(id); }
}
