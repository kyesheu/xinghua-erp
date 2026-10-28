package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.product.bo.LinkErpGoodsSkuBo;
import cn.xinghuaerp.product.entity.ShopGoodsSku;
import cn.xinghuaerp.product.query.ShopGoodsSkuBo;
import java.util.List;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ShopGoodsSkuFacadeImpl implements ShopGoodsSkuFacade {
    private final cn.xinghuaerp.product.service.ShopGoodsSkuService delegate;
    @Override public PageResult<ShopGoodsSku> queryPageList(ShopGoodsSkuBo bo, PageQuery pageQuery) { return delegate.queryPageList(bo, pageQuery); }
    @Override public PageResult<ShopGoodsSku> queryBenShuPageList(ShopGoodsSkuBo bo, PageQuery pageQuery) { return delegate.queryBenShuPageList(bo, pageQuery); }
    @Override public List<ShopGoodsSku> querySkuList(Long shopGoodsId) { return delegate.querySkuList(shopGoodsId); }
    @Override public ResultVo linkErpGoodsSku(LinkErpGoodsSkuBo bo) { return delegate.linkErpGoodsSku(bo); }
    @Override public ShopGoodsSku selectByPlatformSkuId(String platformSkuId,Long shopId) { return delegate.selectByPlatformSkuId(platformSkuId, shopId); }
    @Override public ShopGoodsSku getById(java.io.Serializable id) { return delegate.getById(id); }
    @Override public boolean removeById(java.io.Serializable id) { return delegate.removeById(id); }
}
