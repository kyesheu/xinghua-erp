package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.product.entity.ShopGoodsSkuShipItem;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface ShopGoodsSkuShipItemFacade {
    java.util.List<ShopGoodsSkuShipItem> list();
    java.util.List<ShopGoodsSkuShipItem> list(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopGoodsSkuShipItem> query);
}
