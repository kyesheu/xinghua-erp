package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.SysThirdSystemConfig;
import java.util.List;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface SysThirdSystemConfigFacade {
    List<SysThirdSystemConfig> getConfigListBySystemId(Integer systemId);
    List<SysThirdSystemConfig> getConfigListBySystemId(Integer systemId,Long merchantId);
    List<SysThirdSystemConfig> getConfigListBySystemId(Integer systemId,String systemType);
}
