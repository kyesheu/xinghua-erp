package cn.xinghuaerp.order.service;

import cn.xinghuaerp.order.service.impl.PlatformPullGuard;
import cn.xinghuaerp.order.service.impl.ShopPullApiServiceImpl;
import cn.xinghuaerp.platform.facade.ShopApiCommonFacade;
import cn.xinghuaerp.system.facade.*;
import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.api.ShopApiParams;
import cn.xinghuaerp.open.pdd.PddOrderApiHelper;
import cn.xinghuaerp.open.pdd.model.OrderListResultVo;
import cn.xinghuaerp.open.common.ApiResultVo;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PddOrderPageBoundaryTest {
 @Test void emptyPageWithHasNextIsAnErrorAndCancelledWorkerMakesNoCall() throws Exception {
  var api=mock(ShopApiCommonFacade.class);var shops=mock(OShopFacade.class);var logs=mock(OShopPullLogsFacade.class);
  var shop=new OShop();shop.setId(1L);shop.setType(300);shop.setMerchantId(7L);when(shops.getById(1L)).thenReturn(shop);
  when(api.checkBefore(1L,300)).thenReturn(ResultVo.success(new ShopApiParams()));
  var service=new ShopPullApiServiceImpl(api,shops,null,null,null,null,logs,null,new PlatformPullGuard());
  try(var platform=mockStatic(PddOrderApiHelper.class)) {
   platform.when(()->PddOrderApiHelper.pullOrderList(isNull(),isNull(),isNull(),anyInt(),anyInt(),eq(1),eq(100)))
       .thenReturn(ApiResultVo.success(new OrderListResultVo(true,java.util.List.of())));
   assertNotEquals(0,service.pullOrder(1L,"2026-11-01","2026-11-02").getCode());
   platform.clearInvocations();Thread.currentThread().interrupt();
   try {assertNotEquals(0,service.pullOrder(1L,"2026-11-01","2026-11-02").getCode());platform.verifyNoInteractions();}
   finally {Thread.interrupted();}
  }
 }
}
