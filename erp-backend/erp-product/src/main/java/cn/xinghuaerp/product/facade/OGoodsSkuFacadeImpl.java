package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.product.entity.OGoodsSku;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class OGoodsSkuFacadeImpl implements OGoodsSkuFacade {
    private final cn.xinghuaerp.product.service.OGoodsSkuService delegate;
    @Override public OGoodsSku getGoodsSkuByCode(String skuCode) { return delegate.getGoodsSkuByCode(skuCode); }
    @Override public OGoodsSku getById(java.io.Serializable id) { return delegate.getById(id); }
    @Override public OGoodsSku getOne(com.baomidou.mybatisplus.core.conditions.Wrapper<OGoodsSku> query) { return delegate.getOne(query); }
    @Override public OGoodsSku getOne(com.baomidou.mybatisplus.core.conditions.Wrapper<OGoodsSku> query, boolean throwEx) { return delegate.getOne(query, throwEx); }
}
