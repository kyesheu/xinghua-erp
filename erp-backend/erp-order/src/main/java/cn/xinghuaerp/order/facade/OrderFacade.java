package cn.xinghuaerp.order.facade;

import cn.xinghuaerp.order.entity.ErpSalesOrder;
import cn.xinghuaerp.order.entity.ErpSalesOrderItem;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.entity.OOrderItem;
import cn.xinghuaerp.order.entity.ShopOrder;
import cn.xinghuaerp.order.entity.ShopOrderItem;

/** 模块对外业务契约：跨模块只能通过本接口访问，禁止直接操作 Mapper。 */
public interface OrderFacade {
    java.util.List<ErpSalesOrderItem> listErpSalesOrderItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSalesOrderItem> wrapper);
    int updateErpSalesOrderItem(ErpSalesOrderItem entity, com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSalesOrderItem> wrapper);
    java.util.List<ErpSalesOrder> listErpSalesOrder(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSalesOrder> wrapper);
    int updateErpSalesOrder(ErpSalesOrder entity);
    java.util.List<OOrderItem> listOOrderItemByIds(java.util.Collection<? extends java.io.Serializable> idList);
    java.util.List<OOrderItem> listOOrderItem(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderItem> wrapper);
    int updateOOrderItem(OOrderItem entity, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderItem> wrapper);
    int updateOOrderItem(OOrderItem entity);
    OOrder getOOrderOne(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrder> wrapper);
    int updateOOrder(OOrder entity);
    java.util.List<ShopOrderItem> listShopOrderItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopOrderItem> wrapper);
    int updateShopOrderItem(ShopOrderItem entity, com.baomidou.mybatisplus.core.conditions.Wrapper<ShopOrderItem> wrapper);
    int updateShopOrderItem(ShopOrderItem entity);
    java.util.List<ShopOrder> listShopOrder(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopOrder> wrapper);
    ShopOrder getShopOrderOne(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopOrder> wrapper);
    int updateShopOrder(ShopOrder entity);
}
