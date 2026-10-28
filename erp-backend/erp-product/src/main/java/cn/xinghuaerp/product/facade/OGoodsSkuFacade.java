package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.product.entity.OGoodsSku;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface OGoodsSkuFacade {
    OGoodsSku getGoodsSkuByCode(String skuCode);
    OGoodsSku getById(java.io.Serializable id);
    OGoodsSku getOne(com.baomidou.mybatisplus.core.conditions.Wrapper<OGoodsSku> query);
    OGoodsSku getOne(com.baomidou.mybatisplus.core.conditions.Wrapper<OGoodsSku> query, boolean throwEx);
}
