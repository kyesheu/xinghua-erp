package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.product.entity.ShopGoodsSkuShipItem;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ShopGoodsSkuShipItemFacadeImpl implements ShopGoodsSkuShipItemFacade {
    private final cn.xinghuaerp.product.service.ShopGoodsSkuShipItemService delegate;
    @Override public java.util.List<ShopGoodsSkuShipItem> list() { return delegate.list(); }
    @Override public java.util.List<ShopGoodsSkuShipItem> list(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopGoodsSkuShipItem> query) { return delegate.list(query); }
}
