package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.OShopPlatform;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface OShopPlatformFacade {
    OShopPlatform selectById(Integer id);
    OShopPlatform getById(java.io.Serializable id);
}
