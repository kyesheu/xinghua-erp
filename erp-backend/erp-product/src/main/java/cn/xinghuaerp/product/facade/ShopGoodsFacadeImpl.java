package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.product.request.ShopGoodsAddRequest;
import cn.xinghuaerp.product.request.ShopGoodsSkuAddRequest;
import cn.xinghuaerp.product.request.ShopGoodsSkuInsertRequest;
import cn.xinghuaerp.product.request.ShopGoodsSkuUpdateRequest;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.product.entity.ShopGoods;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ShopGoodsFacadeImpl implements ShopGoodsFacade {
    private final cn.xinghuaerp.product.service.ShopGoodsService delegate;
    @Override public PageResult<ShopGoods> queryPageList(ShopGoods bo, PageQuery pageQuery) { return delegate.queryPageList(bo, pageQuery); }
    @Override public ResultVo<Long> addGoods(ShopGoodsAddRequest goodsAddRequest, OShop shop) { return delegate.addGoods(goodsAddRequest, shop); }
    @Override public ResultVo<Long> addGoodsSku(ShopGoodsSkuAddRequest request) { return delegate.addGoodsSku(request); }
    @Override public ResultVo<Long> insertGoodsSku(ShopGoodsSkuInsertRequest request) { return delegate.insertGoodsSku(request); }
    @Override public ResultVo<Long> updateGoodsSku(ShopGoodsSkuUpdateRequest request) { return delegate.updateGoodsSku(request); }
    @Override public ResultVo<Long> savePddGoods(ShopGoods shopGoods,Long shopId) { return delegate.savePddGoods(shopGoods, shopId); }
    @Override public ShopGoods getById(java.io.Serializable id) { return delegate.getById(id); }
    @Override public boolean removeById(java.io.Serializable id) { return delegate.removeById(id); }
    @Override public boolean updateById(ShopGoods entity) { return delegate.updateById(entity); }
}
