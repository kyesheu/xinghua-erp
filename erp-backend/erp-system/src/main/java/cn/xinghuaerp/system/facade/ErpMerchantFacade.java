package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.ErpMerchant;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface ErpMerchantFacade {
    ErpMerchant getById(java.io.Serializable id);
}
