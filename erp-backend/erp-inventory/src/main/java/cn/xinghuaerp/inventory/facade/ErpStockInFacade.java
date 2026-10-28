package cn.xinghuaerp.inventory.facade;

import cn.xinghuaerp.inventory.entity.ErpStockIn;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface ErpStockInFacade {
    boolean save(ErpStockIn entity);
}
