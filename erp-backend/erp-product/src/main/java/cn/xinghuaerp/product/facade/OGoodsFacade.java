package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.product.entity.OGoods;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface OGoodsFacade {
    OGoods getById(java.io.Serializable id);
}
