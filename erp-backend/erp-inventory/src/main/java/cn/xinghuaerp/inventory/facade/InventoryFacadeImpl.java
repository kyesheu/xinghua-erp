package cn.xinghuaerp.inventory.facade;

import cn.xinghuaerp.inventory.mapper.ErpStockInItemMapper;
import cn.xinghuaerp.inventory.mapper.ErpStockInMapper;
import cn.xinghuaerp.inventory.mapper.ErpStockOutItemMapper;
import cn.xinghuaerp.inventory.mapper.ErpStockOutMapper;
import cn.xinghuaerp.inventory.mapper.ErpWarehouseMapper;
import cn.xinghuaerp.inventory.mapper.OOrderStockingItemMapper;
import cn.xinghuaerp.inventory.mapper.OOrderStockingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import cn.xinghuaerp.inventory.entity.ErpStockIn;
import cn.xinghuaerp.inventory.entity.ErpStockInItem;
import cn.xinghuaerp.inventory.entity.ErpStockOut;
import cn.xinghuaerp.inventory.entity.ErpStockOutItem;
import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.inventory.entity.OOrderStocking;
import cn.xinghuaerp.inventory.entity.OOrderStockingItem;

@Component
@RequiredArgsConstructor
public class InventoryFacadeImpl implements InventoryFacade {
    private final ErpStockInItemMapper erpStockInItemMapper;
    private final ErpStockInMapper erpStockInMapper;
    private final ErpStockOutItemMapper erpStockOutItemMapper;
    private final ErpStockOutMapper erpStockOutMapper;
    private final ErpWarehouseMapper erpWarehouseMapper;
    private final OOrderStockingItemMapper oOrderStockingItemMapper;
    private final OOrderStockingMapper oOrderStockingMapper;

    @Override public int saveErpStockInItem(ErpStockInItem entity) { return erpStockInItemMapper.insert(entity); }
    @Override public int saveErpStockIn(ErpStockIn entity) { return erpStockInMapper.insert(entity); }
    @Override public java.util.List<ErpStockIn> listErpStockIn(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpStockIn> wrapper) { return erpStockInMapper.selectList(wrapper); }
    @Override public ErpStockIn getErpStockInOne(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpStockIn> wrapper) { return erpStockInMapper.selectOne(wrapper); }
    @Override public int saveErpStockOutItem(ErpStockOutItem entity) { return erpStockOutItemMapper.insert(entity); }
    @Override public java.util.List<ErpStockOutItem> listErpStockOutItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpStockOutItem> wrapper) { return erpStockOutItemMapper.selectList(wrapper); }
    @Override public int saveErpStockOut(ErpStockOut entity) { return erpStockOutMapper.insert(entity); }
    @Override public java.util.List<ErpStockOut> listErpStockOut(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpStockOut> wrapper) { return erpStockOutMapper.selectList(wrapper); }
    @Override public int saveErpWarehouse(ErpWarehouse entity) { return erpWarehouseMapper.insert(entity); }
    @Override public ErpWarehouse getErpWarehouseById(java.io.Serializable id) { return erpWarehouseMapper.selectById(id); }
    @Override public int deleteOOrderStockingItem(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper) { return oOrderStockingItemMapper.delete(wrapper); }
    @Override public int saveOOrderStockingItem(OOrderStockingItem entity) { return oOrderStockingItemMapper.insert(entity); }
    @Override public Long countOOrderStockingItem(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper) { return oOrderStockingItemMapper.selectCount(wrapper); }
    @Override public java.util.List<OOrderStockingItem> listOOrderStockingItem(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper) { return oOrderStockingItemMapper.selectList(wrapper); }
    @Override public com.baomidou.mybatisplus.extension.plugins.pagination.Page<OOrderStockingItem> pageOOrderStockingItem(com.baomidou.mybatisplus.extension.plugins.pagination.Page<OOrderStockingItem> page, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper) { return oOrderStockingItemMapper.selectPage(page, wrapper); }
    @Override public int updateOOrderStockingItem(OOrderStockingItem entity, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper) { return oOrderStockingItemMapper.update(entity, wrapper); }
    @Override public int updateOOrderStockingItem(OOrderStockingItem entity) { return oOrderStockingItemMapper.updateById(entity); }
    @Override public int deleteOOrderStockingById(java.io.Serializable id) { return oOrderStockingMapper.deleteById(id); }
    @Override public int saveOOrderStocking(OOrderStocking entity) { return oOrderStockingMapper.insert(entity); }
    @Override public OOrderStocking getOOrderStockingById(java.io.Serializable id) { return oOrderStockingMapper.selectById(id); }
    @Override public Long countOOrderStocking(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStocking> wrapper) { return oOrderStockingMapper.selectCount(wrapper); }
    @Override public java.util.List<OOrderStocking> listOOrderStocking(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStocking> wrapper) { return oOrderStockingMapper.selectList(wrapper); }
    @Override public OOrderStocking getOOrderStockingOne(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStocking> wrapper) { return oOrderStockingMapper.selectOne(wrapper); }
    @Override public com.baomidou.mybatisplus.extension.plugins.pagination.Page<OOrderStocking> pageOOrderStocking(com.baomidou.mybatisplus.extension.plugins.pagination.Page<OOrderStocking> page, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStocking> wrapper) { return oOrderStockingMapper.selectPage(page, wrapper); }
    @Override public int updateOOrderStocking(OOrderStocking entity, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStocking> wrapper) { return oOrderStockingMapper.update(entity, wrapper); }
    @Override public int updateOOrderStocking(OOrderStocking entity) { return oOrderStockingMapper.updateById(entity); }
    @org.springframework.beans.factory.annotation.Autowired
    private cn.xinghuaerp.inventory.mapper.OOrderStockingItemBatchMapper stockingBatchMapper;
    @Override public java.util.List<cn.xinghuaerp.inventory.entity.OOrderStockingItemBatch> listStockingBatches(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.xinghuaerp.inventory.entity.OOrderStockingItemBatch> wrapper) { return stockingBatchMapper.selectList(wrapper); }
    @Override public OOrderStockingItem getStockingItemById(java.io.Serializable id) { return oOrderStockingItemMapper.selectById(id); }

    @org.springframework.beans.factory.annotation.Autowired
    private cn.xinghuaerp.inventory.service.ShopOrderStockLockService shopOrderStockLockService;

    @Override
    public String checkStockAvailable(Long merchantId, java.util.Map<Long, Integer> skuQuantities) {
        if (skuQuantities == null || skuQuantities.isEmpty()) return null;
        return shopOrderStockLockService.checkAvailable(merchantId, skuQuantities);
    }

    @Override
    public void lockStockForOrder(Long orderId, Long merchantId, java.util.Map<Long, Integer> skuQuantities,
                                  java.time.LocalDateTime expireTime) {
        if (skuQuantities == null || skuQuantities.isEmpty()) return;
        shopOrderStockLockService.lockForOrder(orderId, merchantId, skuQuantities, expireTime);
    }

    @Override
    public int releaseOrderStock(Long orderId) {
        return shopOrderStockLockService.releaseForOrder(orderId);
    }

    @Override
    public int releaseExpiredStockLocks() {
        return shopOrderStockLockService.releaseExpired();
    }
}
