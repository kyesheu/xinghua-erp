package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.OShopPullLogs;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class OShopPullLogsFacadeImpl implements OShopPullLogsFacade {
    private final cn.xinghuaerp.system.service.OShopPullLogsService delegate;
    @Override public boolean save(OShopPullLogs entity) { return delegate.save(entity); }
}
