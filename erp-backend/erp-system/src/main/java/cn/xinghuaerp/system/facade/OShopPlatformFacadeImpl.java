package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.OShopPlatform;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class OShopPlatformFacadeImpl implements OShopPlatformFacade {
    private final cn.xinghuaerp.system.service.OShopPlatformService delegate;
    @Override public OShopPlatform selectById(Integer id) { return delegate.selectById(id); }
    @Override public OShopPlatform getById(java.io.Serializable id) { return delegate.getById(id); }
}
