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
}
