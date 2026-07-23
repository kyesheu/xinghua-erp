package cn.xinghuaerp.product.mapper;


import cn.xinghuaerp.product.entity.ShopGoodsSku;
import cn.xinghuaerp.product.query.ShopGoodsSkuBo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

/**
* @author qilip
* @description 针对表【oms_shop_goods_sku(其他渠道店铺商品SKU)】的数据库操作Mapper
* @createDate 2025-07-15 08:29:21
* @Entity cn.xinghuaerp.product.entity.ShopGoodsSku
*/
public interface ShopGoodsSkuMapper extends BaseMapper<ShopGoodsSku> {
    Page<ShopGoodsSku> selectBenshuPageVo(@Param("page") Page<ShopGoodsSku> page, @Param("qw") ShopGoodsSkuBo qw);
}




