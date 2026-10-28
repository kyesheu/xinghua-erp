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

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface ShopGoodsFacade {
    PageResult<ShopGoods> queryPageList(ShopGoods bo, PageQuery pageQuery);
    ResultVo<Long> addGoods(ShopGoodsAddRequest goodsAddRequest, OShop shop);
    ResultVo<Long> addGoodsSku(ShopGoodsSkuAddRequest request);
    ResultVo<Long> insertGoodsSku(ShopGoodsSkuInsertRequest request);
    ResultVo<Long> updateGoodsSku(ShopGoodsSkuUpdateRequest request);
    ResultVo<Long> savePddGoods(ShopGoods shopGoods,Long shopId);
    ShopGoods getById(java.io.Serializable id);
    boolean removeById(java.io.Serializable id);
    boolean updateById(ShopGoods entity);
}
