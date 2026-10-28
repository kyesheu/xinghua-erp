package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.ErpMerchant;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ErpMerchantFacadeImpl implements ErpMerchantFacade {
    private final cn.xinghuaerp.system.service.ErpMerchantService delegate;
    @Override public ErpMerchant getById(java.io.Serializable id) { return delegate.getById(id); }
}
