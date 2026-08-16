package cn.xinghuaerp.order.mapper;

import cn.xinghuaerp.order.entity.ShopOrder;
import cn.xinghuaerp.order.query.ShopOrderQueryBo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

/**
* @author qilip
* @description 针对表【oms_shop_order】的数据库操作Mapper
* @createDate 2025-07-15 11:36:42
* @Entity cn.xinghuaerp.order.entity.ShopOrder
*/
public interface ShopOrderMapper extends BaseMapper<ShopOrder> {
    Page<ShopOrder> searchOrderByGoodsParam(@Param("page") Page<ShopOrder> page, @Param("qw") ShopOrderQueryBo qw);
}




