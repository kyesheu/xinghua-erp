package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.product.entity.OGoods;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class OGoodsFacadeImpl implements OGoodsFacade {
    private final cn.xinghuaerp.product.service.OGoodsService delegate;
    @Override public OGoods getById(java.io.Serializable id) { return delegate.getById(id); }
}
