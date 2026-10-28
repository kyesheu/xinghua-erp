package cn.xinghuaerp.purchase.facade;

import cn.xinghuaerp.purchase.entity.ErpSupplier;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface ErpSupplierFacade {
    ErpSupplier getById(java.io.Serializable id);
}
