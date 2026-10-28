package cn.xinghuaerp.inventory.mapper;

import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 主系统商品库存 Mapper
 *
 * @author xinghua
 * @description 针对表【o_goods_inventory(主系统商品库存)】的数据库操作Mapper
 */
public interface OGoodsInventoryMapper extends BaseMapper<OGoodsInventory> {

    @org.apache.ibatis.annotations.Update("UPDATE o_goods_inventory SET quantity=quantity+#{delta}, available_quantity=available_quantity+#{delta} WHERE id=#{id} AND is_delete=0 AND quantity+#{delta} BETWEEN 0 AND 2147483647 AND available_quantity+#{delta} BETWEEN 0 AND 2147483647")
    int adjustStock(@org.apache.ibatis.annotations.Param("id") Long id, @org.apache.ibatis.annotations.Param("delta") long delta);

    @org.apache.ibatis.annotations.Update("UPDATE o_goods_inventory SET locked_quantity=locked_quantity+#{delta}, available_quantity=available_quantity-#{delta} WHERE id=#{id} AND is_delete=0 AND locked_quantity+#{delta} BETWEEN 0 AND 2147483647 AND available_quantity-#{delta} BETWEEN 0 AND 2147483647")
    int adjustLock(@org.apache.ibatis.annotations.Param("id") Long id, @org.apache.ibatis.annotations.Param("delta") long delta);
}
