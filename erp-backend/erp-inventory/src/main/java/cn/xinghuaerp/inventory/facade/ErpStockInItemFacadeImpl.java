package cn.xinghuaerp.inventory.facade;

import cn.xinghuaerp.inventory.entity.ErpStockInItem;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ErpStockInItemFacadeImpl implements ErpStockInItemFacade {
    private final cn.xinghuaerp.inventory.service.ErpStockInItemService delegate;
    @Override public boolean save(ErpStockInItem entity) { return delegate.save(entity); }
}
