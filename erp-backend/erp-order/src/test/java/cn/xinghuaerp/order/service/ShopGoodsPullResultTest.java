package cn.xinghuaerp.order.service;
import cn.xinghuaerp.order.service.impl.ShopPullApiServiceImpl;
import cn.xinghuaerp.product.facade.ShopGoodsFacade;
import cn.xinghuaerp.product.entity.ShopGoods;
import cn.xinghuaerp.common.ResultVo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ShopGoodsPullResultTest {
 @Test void unsuccessfulSaveIsNotCountedAsSuccess() throws Exception {
  var goods=mock(ShopGoodsFacade.class);
  var service=new ShopPullApiServiceImpl(null,null,null,null,goods,null,null,null);
  var method=ShopPullApiServiceImpl.class.getDeclaredMethod("savePulledGoods",ShopGoods.class,Long.class);method.setAccessible(true);
  var item=new ShopGoods();when(goods.savePddGoods(item,1L)).thenReturn(ResultVo.error("database rejected"));
  var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(service,item,1L));
  assertInstanceOf(IllegalStateException.class,error.getCause());
  when(goods.savePddGoods(item,1L)).thenReturn(ResultVo.success(2L));
  assertDoesNotThrow(()->method.invoke(service,item,1L));
 }
}
