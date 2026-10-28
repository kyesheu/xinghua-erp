package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.SysThirdSystemConfig;
import java.util.List;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class SysThirdSystemConfigFacadeImpl implements SysThirdSystemConfigFacade {
    private final cn.xinghuaerp.system.service.SysThirdSystemConfigService delegate;
    @Override public List<SysThirdSystemConfig> getConfigListBySystemId(Integer systemId) { return delegate.getConfigListBySystemId(systemId); }
    @Override public List<SysThirdSystemConfig> getConfigListBySystemId(Integer systemId,Long merchantId) { return delegate.getConfigListBySystemId(systemId, merchantId); }
    @Override public List<SysThirdSystemConfig> getConfigListBySystemId(Integer systemId,String systemType) { return delegate.getConfigListBySystemId(systemId, systemType); }
}
