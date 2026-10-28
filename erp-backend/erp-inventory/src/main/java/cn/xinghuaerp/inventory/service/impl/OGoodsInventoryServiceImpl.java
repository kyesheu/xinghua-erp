package cn.xinghuaerp.inventory.service.impl;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.inventory.mapper.OGoodsInventoryMapper;
import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 主系统商品库存 Service 实现
 *
 * @author xinghua
 */
@AllArgsConstructor
@Service
public class OGoodsInventoryServiceImpl
        extends ServiceImpl<OGoodsInventoryMapper, OGoodsInventory>
        implements OGoodsInventoryService {

    @Override
    public PageResult<OGoodsInventory> queryPageList(OGoodsInventory bo, PageQuery pageQuery) {
        LambdaQueryWrapper<OGoodsInventory> qw = new LambdaQueryWrapper<OGoodsInventory>()
                .eq(bo.getIsDelete() != null, OGoodsInventory::getIsDelete, bo.getIsDelete())
                .eq(bo.getWarehouseId() != null, OGoodsInventory::getWarehouseId, bo.getWarehouseId())
                .eq(bo.getMerchantId() != null, OGoodsInventory::getMerchantId, bo.getMerchantId())
                .eq(bo.getShopId() != null, OGoodsInventory::getShopId, bo.getShopId())
                .eq(bo.getSkuId() != null, OGoodsInventory::getSkuId, bo.getSkuId())
                .eq(bo.getGoodsId() != null, OGoodsInventory::getGoodsId, bo.getGoodsId())
                .eq(bo.getSkuCode() != null && !bo.getSkuCode().isEmpty(), OGoodsInventory::getSkuCode, bo.getSkuCode())
                .like(bo.getGoodsName() != null, OGoodsInventory::getGoodsName, bo.getGoodsName());
        // 默认只查未删除的
        if (bo.getIsDelete() == null) {
            qw.eq(OGoodsInventory::getIsDelete, 0);
        }
        IPage<OGoodsInventory> page = this.baseMapper.selectPage(pageQuery.build(), qw);
        return PageResult.build(page);
    }

    @Override
    public boolean deductStock(Long inventoryId, Integer quantity) {
        return inventoryId != null && quantity != null && quantity > 0
                && baseMapper.adjustStock(inventoryId, -quantity) == 1;
    }

    @Override
    public boolean addStock(Long inventoryId, Integer quantity) {
        return inventoryId != null && quantity != null && quantity > 0
                && baseMapper.adjustStock(inventoryId, quantity) == 1;
    }

    @Override
    public boolean lockStock(Long inventoryId, Integer quantity) {
        return inventoryId != null && quantity != null && quantity != 0
                && baseMapper.adjustLock(inventoryId, quantity.longValue()) == 1;
    }
}
