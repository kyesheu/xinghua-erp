package cn.xinghuaerp.inventory.facade;

import cn.xinghuaerp.inventory.entity.ErpWarehouse;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ErpWarehouseFacadeImpl implements ErpWarehouseFacade {
    private final cn.xinghuaerp.inventory.service.ErpWarehouseService delegate;
    @Override public ErpWarehouse getById(java.io.Serializable id) { return delegate.getById(id); }
}
