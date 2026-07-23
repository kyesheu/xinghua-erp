package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.product.entity.ErpSupplierProductItem;
import cn.xinghuaerp.product.entity.OGoods;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.product.entity.ShopGoods;
import cn.xinghuaerp.product.entity.ShopGoodsSku;

/** 模块对外业务契约：跨模块只能通过本接口访问，禁止直接操作 Mapper。 */
public interface ProductFacade {
    int deleteErpSupplierProductItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSupplierProductItem> wrapper);
    int deleteErpSupplierProductItemById(java.io.Serializable id);
    int saveErpSupplierProductItem(ErpSupplierProductItem entity);
    Long countErpSupplierProductItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSupplierProductItem> wrapper);
    java.util.List<ErpSupplierProductItem> listErpSupplierProductItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSupplierProductItem> wrapper);
    ErpSupplierProductItem getErpSupplierProductItemOne(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSupplierProductItem> wrapper);
    int updateErpSupplierProductItem(ErpSupplierProductItem entity);
    OGoods getOGoodsById(java.io.Serializable id);
    OGoodsSku getOGoodsSkuById(java.io.Serializable id);
    java.util.List<OGoodsSku> listOGoodsSku(com.baomidou.mybatisplus.core.conditions.Wrapper<OGoodsSku> wrapper);
    java.util.List<ShopGoods> listShopGoods(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopGoods> wrapper);
    ShopGoodsSku getShopGoodsSkuById(java.io.Serializable id);
    java.util.List<ShopGoodsSku> listShopGoodsSku(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopGoodsSku> wrapper);
}
