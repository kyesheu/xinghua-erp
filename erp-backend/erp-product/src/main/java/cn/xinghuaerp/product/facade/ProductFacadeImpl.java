package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.product.mapper.ErpSupplierProductItemMapper;
import cn.xinghuaerp.product.mapper.OGoodsMapper;
import cn.xinghuaerp.product.mapper.OGoodsSkuMapper;
import cn.xinghuaerp.product.mapper.ShopGoodsMapper;
import cn.xinghuaerp.product.mapper.ShopGoodsSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import cn.xinghuaerp.product.entity.ErpSupplierProductItem;
import cn.xinghuaerp.product.entity.OGoods;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.product.entity.ShopGoods;
import cn.xinghuaerp.product.entity.ShopGoodsSku;

@Component
@RequiredArgsConstructor
public class ProductFacadeImpl implements ProductFacade {
    private final ErpSupplierProductItemMapper erpSupplierProductItemMapper;
    private final OGoodsMapper oGoodsMapper;
    private final OGoodsSkuMapper oGoodsSkuMapper;
    private final ShopGoodsMapper shopGoodsMapper;
    private final ShopGoodsSkuMapper shopGoodsSkuMapper;

    @Override public int deleteErpSupplierProductItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSupplierProductItem> wrapper) { return erpSupplierProductItemMapper.delete(wrapper); }
    @Override public int deleteErpSupplierProductItemById(java.io.Serializable id) { return erpSupplierProductItemMapper.deleteById(id); }
    @Override public int saveErpSupplierProductItem(ErpSupplierProductItem entity) { return erpSupplierProductItemMapper.insert(entity); }
    @Override public Long countErpSupplierProductItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSupplierProductItem> wrapper) { return erpSupplierProductItemMapper.selectCount(wrapper); }
    @Override public java.util.List<ErpSupplierProductItem> listErpSupplierProductItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSupplierProductItem> wrapper) { return erpSupplierProductItemMapper.selectList(wrapper); }
    @Override public ErpSupplierProductItem getErpSupplierProductItemOne(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSupplierProductItem> wrapper) { return erpSupplierProductItemMapper.selectOne(wrapper); }
    @Override public int updateErpSupplierProductItem(ErpSupplierProductItem entity) { return erpSupplierProductItemMapper.updateById(entity); }
    @Override public OGoods getOGoodsById(java.io.Serializable id) { return oGoodsMapper.selectById(id); }
    @Override public OGoodsSku getOGoodsSkuById(java.io.Serializable id) { return oGoodsSkuMapper.selectById(id); }
    @Override public java.util.List<OGoodsSku> listOGoodsSku(com.baomidou.mybatisplus.core.conditions.Wrapper<OGoodsSku> wrapper) { return oGoodsSkuMapper.selectList(wrapper); }
    @Override public java.util.List<ShopGoods> listShopGoods(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopGoods> wrapper) { return shopGoodsMapper.selectList(wrapper); }
    @Override public ShopGoodsSku getShopGoodsSkuById(java.io.Serializable id) { return shopGoodsSkuMapper.selectById(id); }
    @Override public java.util.List<ShopGoodsSku> listShopGoodsSku(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopGoodsSku> wrapper) { return shopGoodsSkuMapper.selectList(wrapper); }
}
