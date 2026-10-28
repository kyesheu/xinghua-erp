package cn.xinghuaerp.inventory.facade;

import cn.xinghuaerp.inventory.entity.ErpStockIn;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ErpStockInFacadeImpl implements ErpStockInFacade {
    private final cn.xinghuaerp.inventory.service.ErpStockInService delegate;
    @Override public boolean save(ErpStockIn entity) { return delegate.save(entity); }
}
