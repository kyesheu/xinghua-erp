package cn.xinghuaerp.order.service;
import cn.xinghuaerp.order.service.impl.ShopPullApiServiceImpl;
import cn.xinghuaerp.platform.facade.ShopApiCommonFacade;
import cn.xinghuaerp.system.facade.*;
import cn.xinghuaerp.product.facade.ShopGoodsFacade;
import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.api.ShopApiParams;
import cn.xinghuaerp.open.pdd.PddGoodsApiHelper;
import cn.xinghuaerp.open.pdd.model.*;
import cn.xinghuaerp.open.common.ApiResultVo;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PddGoodsPaginationTest {
 @Test void secondPageIsSavedAndFailedPageDoesNotAdvanceSuccessCheckpoint() throws Exception {
  var api=mock(ShopApiCommonFacade.class);var shops=mock(OShopFacade.class);var goods=mock(ShopGoodsFacade.class);var times=mock(OShopPullLasttimeFacade.class);var logs=mock(OShopPullLogsFacade.class);
  var shop=new OShop();shop.setId(1L);shop.setType(300);shop.setMerchantId(7L);when(shops.getById(1L)).thenReturn(shop);
  var config=new ShopApiParams();config.setAppKey("offline");config.setAppSecret("offline");config.setAccessToken("offline");when(api.checkBefore(1L,300)).thenReturn(ResultVo.success(config));
  when(times.advance(eq(1L),eq("GOODS"),any())).thenReturn(true);
  var service=new ShopPullApiServiceImpl(api,shops,null,null,goods,null,logs,times);
  var item=new Goods();item.setGoodsId(1L);item.setSkuList(List.of());when(goods.savePddGoods(any(),eq(1L))).thenReturn(ResultVo.success(1L));
  try(var platform=mockStatic(PddGoodsApiHelper.class)) {
   platform.when(()->PddGoodsApiHelper.pullGoodsList("offline","offline","offline",1,20)).thenReturn(ApiResultVo.success(new GoodsResultVo(21,Collections.nCopies(20,item))));
   platform.when(()->PddGoodsApiHelper.pullGoodsList("offline","offline","offline",2,20)).thenReturn(ApiResultVo.success(new GoodsResultVo(21,List.of(item))));
   assertEquals(0,service.pullGoods(1L,0).getCode());verify(goods,times(21)).savePddGoods(any(),eq(1L));verify(times).advance(eq(1L),eq("GOODS"),any());
   when(times.advance(eq(1L),eq("GOODS"),any())).thenReturn(false);
   assertNotEquals(0,service.pullGoods(1L,0).getCode());
   clearInvocations(times);
   var error=new ApiResultVo<GoodsResultVo>();error.setCode(500);
   platform.when(()->PddGoodsApiHelper.pullGoodsList("offline","offline","offline",2,20)).thenReturn(error);
   assertNotEquals(0,service.pullGoods(1L,0).getCode());verify(times,never()).advance(any(),any(),any());verify(times,never()).updateById(any());
  }
 }
}
