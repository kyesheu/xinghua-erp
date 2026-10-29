package cn.xinghuaerp.open.jd;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class JdGoodsPaginationTest {
 @Test void failedLaterPageCannotBeReturnedAsCompleteSuccess() {
  try(var api=mockStatic(JdGoodsApiHelper.class)) {
   api.when(()->JdGoodsApiHelper.pullGoodsSkuList(1,50,"offline","offline","offline"))
    .thenReturn("{\"jingdong_sku_read_searchSkuList_responce\":{\"code\":\"0\",\"page\":{\"totalItem\":51,\"data\":[]}}}");
   api.when(()->JdGoodsApiHelper.pullGoodsSkuList(2,50,"offline","offline","offline")).thenReturn("");
   api.when(()->JdGoodsApiHelper.pullGoodsSkuList("offline","offline","offline")).thenCallRealMethod();
   assertNotEquals(0,JdGoodsApiHelper.pullGoodsSkuList("offline","offline","offline").getCode());
  }
 }
}
