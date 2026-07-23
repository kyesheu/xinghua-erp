package cn.xinghuaerp.product.mapper;

import cn.xinghuaerp.product.entity.OGoods;
import cn.xinghuaerp.product.vo.GoodsSpecListVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;


import java.util.List;

/**
* @author TW
* @description 针对表【o_goods(商品库存管理)】的数据库操作Mapper
* @createDate 2024-03-11 14:24:49
* @Entity cn.xinghuaerp.product.entity.OGoods
*/
public interface OGoodsMapper extends BaseMapper<OGoods> {
    List<GoodsSpecListVo> searchGoodsSpec(@Param("merchantId") Long merchantId,@Param("keyword") String keyword);
    List<GoodsSpecListVo> getVendorGoodsSpecByCode(@Param("vendorId") Long vendorId,@Param("skuCode") String skuCode);
}




