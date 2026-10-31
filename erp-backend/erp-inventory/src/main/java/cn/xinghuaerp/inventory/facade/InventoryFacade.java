package cn.xinghuaerp.inventory.facade;

import cn.xinghuaerp.inventory.entity.ErpStockIn;
import cn.xinghuaerp.inventory.entity.ErpStockInItem;
import cn.xinghuaerp.inventory.entity.ErpStockOut;
import cn.xinghuaerp.inventory.entity.ErpStockOutItem;
import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.inventory.entity.OOrderStocking;
import cn.xinghuaerp.inventory.entity.OOrderStockingItem;

/** 模块对外业务契约：跨模块只能通过本接口访问，禁止直接操作 Mapper。 */
public interface InventoryFacade {
    int saveErpStockInItem(ErpStockInItem entity);
    int saveErpStockIn(ErpStockIn entity);
    java.util.List<ErpStockIn> listErpStockIn(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpStockIn> wrapper);
    ErpStockIn getErpStockInOne(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpStockIn> wrapper);
    int saveErpStockOutItem(ErpStockOutItem entity);
    java.util.List<ErpStockOutItem> listErpStockOutItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpStockOutItem> wrapper);
    int saveErpStockOut(ErpStockOut entity);
    java.util.List<ErpStockOut> listErpStockOut(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpStockOut> wrapper);
    int saveErpWarehouse(ErpWarehouse entity);
    ErpWarehouse getErpWarehouseById(java.io.Serializable id);
    int deleteOOrderStockingItem(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper);
    int saveOOrderStockingItem(OOrderStockingItem entity);
    Long countOOrderStockingItem(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper);
    java.util.List<OOrderStockingItem> listOOrderStockingItem(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<OOrderStockingItem> pageOOrderStockingItem(com.baomidou.mybatisplus.extension.plugins.pagination.Page<OOrderStockingItem> page, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper);
    int updateOOrderStockingItem(OOrderStockingItem entity, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper);
    int updateOOrderStockingItem(OOrderStockingItem entity);
    int deleteOOrderStockingById(java.io.Serializable id);
    int saveOOrderStocking(OOrderStocking entity);
    OOrderStocking getOOrderStockingById(java.io.Serializable id);
    Long countOOrderStocking(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStocking> wrapper);
    java.util.List<OOrderStocking> listOOrderStocking(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStocking> wrapper);
    OOrderStocking getOOrderStockingOne(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStocking> wrapper);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<OOrderStocking> pageOOrderStocking(com.baomidou.mybatisplus.extension.plugins.pagination.Page<OOrderStocking> page, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStocking> wrapper);
    int updateOOrderStocking(OOrderStocking entity, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStocking> wrapper);
    int updateOOrderStocking(OOrderStocking entity);
    java.util.List<cn.xinghuaerp.inventory.entity.OOrderStockingItemBatch> listStockingBatches(com.baomidou.mybatisplus.core.conditions.Wrapper<cn.xinghuaerp.inventory.entity.OOrderStockingItemBatch> wrapper);
    OOrderStockingItem getStockingItemById(java.io.Serializable id);

    /**
     * 下单前校验可用库存。{@code skuQuantities} 为「SKU ID → 需要数量」，
     * 同一 SKU 的多行明细由调用方先合并。返回 {@code null} 表示够，否则是提示文案。
     */
    String checkStockAvailable(Long merchantId, java.util.Map<Long, Integer> skuQuantities);

    /**
     * 按订单锁定库存，落 {@code o_shop_order_stock_lock} 记录。
     * 必须先 {@link #checkStockAvailable} 通过；锁的瞬间被并发订单抢走会抛异常并回滚本次全部锁定。
     */
    void lockStockForOrder(Long orderId, Long merchantId, java.util.Map<Long, Integer> skuQuantities,
                           java.time.LocalDateTime expireTime);

    /** 释放某订单仍在锁定中的库存（取消订单时调用），幂等。返回释放条数。 */
    int releaseOrderStock(Long orderId);

    /** 释放所有已过期的订单库存锁定，返回处理条数。定时任务兜底异常路径。 */
    int releaseExpiredStockLocks();
}
