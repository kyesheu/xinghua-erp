package cn.xinghuaerp.order.service;

import org.junit.jupiter.api.Test;
import cn.xinghuaerp.product.mapper.OGoodsMapper;
import cn.xinghuaerp.product.mapper.OGoodsSkuMapper;
import cn.xinghuaerp.inventory.mapper.ErpWarehouseMapper;
import cn.xinghuaerp.order.mapper.OOrderItemMapper;
import cn.xinghuaerp.order.mapper.OOrderMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.entity.OOrderItem;
import cn.xinghuaerp.product.service.OGoodsSkuService;
import cn.xinghuaerp.order.service.impl.OOrderItemServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderItemSkuUpdateTest {
 @Test void allocatedOrUnknownRefundCannotChangeSku() {
  var items=mock(OOrderItemMapper.class); var orders=mock(OOrderMapper.class); var skus=mock(OGoodsSkuService.class);
  var service=new OOrderItemServiceImpl(items,orders,mock(cn.xinghuaerp.purchase.facade.PurchaseFacade.class),mock(cn.xinghuaerp.product.facade.ProductFacade.class),skus,mock(cn.xinghuaerp.product.facade.ProductFacade.class),mock(cn.xinghuaerp.inventory.facade.InventoryFacade.class));
  var item=new OOrderItem(); item.setOrderId("1"); item.setRefundStatus(1); item.setShipStatus(1); item.setHasPushErp(0);
  when(items.selectById(1L)).thenReturn(item);
  when(items.selectOne(any())).thenReturn(item);
  when(orders.selectOne(any())).thenReturn(new OOrder());
  assertNotEquals(0,service.updateErpSkuId(1L,"2").getCode());
  item.setShipStatus(0); item.setHasPushErp(1);
  assertNotEquals(0,service.updateErpSkuId(1L,"2").getCode());
  item.setHasPushErp(0); item.setRefundStatus(null);
  assertNotEquals(0,service.updateErpSkuId(1L,"2").getCode());
  verifyNoInteractions(skus);
  verify(items,never()).updateById(any(OOrderItem.class));
 }
}


