package cn.xinghuaerp.order.facade;

import cn.xinghuaerp.order.mapper.ErpSalesOrderItemMapper;
import cn.xinghuaerp.order.mapper.ErpSalesOrderMapper;
import cn.xinghuaerp.order.mapper.OOrderItemMapper;
import cn.xinghuaerp.order.mapper.OOrderMapper;
import cn.xinghuaerp.order.mapper.ShopOrderItemMapper;
import cn.xinghuaerp.order.mapper.ShopOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import cn.xinghuaerp.order.entity.ErpSalesOrder;
import cn.xinghuaerp.order.entity.ErpSalesOrderItem;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.entity.OOrderItem;
import cn.xinghuaerp.order.entity.ShopOrder;
import cn.xinghuaerp.order.entity.ShopOrderItem;

@Component
@RequiredArgsConstructor
public class OrderFacadeImpl implements OrderFacade {
    private final ErpSalesOrderItemMapper erpSalesOrderItemMapper;
    private final ErpSalesOrderMapper erpSalesOrderMapper;
    private final OOrderItemMapper oOrderItemMapper;
    private final OOrderMapper oOrderMapper;
    private final ShopOrderItemMapper shopOrderItemMapper;
    private final ShopOrderMapper shopOrderMapper;

    @Override public java.util.List<ErpSalesOrderItem> listErpSalesOrderItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSalesOrderItem> wrapper) { return erpSalesOrderItemMapper.selectList(wrapper); }
    @Override public int updateErpSalesOrderItem(ErpSalesOrderItem entity, com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSalesOrderItem> wrapper) { return erpSalesOrderItemMapper.update(entity, wrapper); }
    @Override public java.util.List<ErpSalesOrder> listErpSalesOrder(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSalesOrder> wrapper) { return erpSalesOrderMapper.selectList(wrapper); }
    @Override public int updateErpSalesOrder(ErpSalesOrder entity) { return erpSalesOrderMapper.updateById(entity); }
    @Override public java.util.List<OOrderItem> listOOrderItemByIds(java.util.Collection<? extends java.io.Serializable> idList) { return oOrderItemMapper.selectBatchIds(idList); }
    @Override public java.util.List<OOrderItem> listOOrderItem(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderItem> wrapper) { return oOrderItemMapper.selectList(wrapper); }
    @Override public int updateOOrderItem(OOrderItem entity, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderItem> wrapper) { return oOrderItemMapper.update(entity, wrapper); }
    @Override public int updateOOrderItem(OOrderItem entity) { return oOrderItemMapper.updateById(entity); }
    @Override public OOrder getOOrderOne(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrder> wrapper) { return oOrderMapper.selectOne(wrapper); }
    @Override public int updateOOrder(OOrder entity) { return oOrderMapper.updateById(entity); }
    @Override public java.util.List<ShopOrderItem> listShopOrderItem(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopOrderItem> wrapper) { return shopOrderItemMapper.selectList(wrapper); }
    @Override public int updateShopOrderItem(ShopOrderItem entity, com.baomidou.mybatisplus.core.conditions.Wrapper<ShopOrderItem> wrapper) { return shopOrderItemMapper.update(entity, wrapper); }
    @Override public int updateShopOrderItem(ShopOrderItem entity) { return shopOrderItemMapper.updateById(entity); }
    @Override public java.util.List<ShopOrder> listShopOrder(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopOrder> wrapper) { return shopOrderMapper.selectList(wrapper); }
    @Override public ShopOrder getShopOrderOne(com.baomidou.mybatisplus.core.conditions.Wrapper<ShopOrder> wrapper) { return shopOrderMapper.selectOne(wrapper); }
    @Override public int updateShopOrder(ShopOrder entity) { return shopOrderMapper.updateById(entity); }
    @Override public OOrder getOOrderById(java.io.Serializable id) { return oOrderMapper.selectById(id); }
    @Override public com.baomidou.mybatisplus.extension.plugins.pagination.Page<OOrder> pageOOrder(com.baomidou.mybatisplus.extension.plugins.pagination.Page<OOrder> page, com.baomidou.mybatisplus.core.conditions.Wrapper<OOrder> wrapper) { return oOrderMapper.selectPage(page, wrapper); }
}
