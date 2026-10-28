package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.OShopPullLogs;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface OShopPullLogsFacade {
    boolean save(OShopPullLogs entity);
}
