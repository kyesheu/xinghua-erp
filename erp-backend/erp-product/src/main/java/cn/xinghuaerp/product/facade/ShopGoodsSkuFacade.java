package cn.xinghuaerp.product.facade;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.product.bo.LinkErpGoodsSkuBo;
import cn.xinghuaerp.product.entity.ShopGoodsSku;
import cn.xinghuaerp.product.query.ShopGoodsSkuBo;
import java.util.List;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface ShopGoodsSkuFacade {
    PageResult<ShopGoodsSku> queryPageList(ShopGoodsSkuBo bo, PageQuery pageQuery);
    PageResult<ShopGoodsSku> queryBenShuPageList(ShopGoodsSkuBo bo, PageQuery pageQuery);
    List<ShopGoodsSku> querySkuList(Long shopGoodsId);
    ResultVo linkErpGoodsSku(LinkErpGoodsSkuBo bo);
    ShopGoodsSku selectByPlatformSkuId(String platformSkuId,Long shopId);
    ShopGoodsSku getById(java.io.Serializable id);
    boolean removeById(java.io.Serializable id);
}
