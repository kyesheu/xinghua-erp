package cn.xinghuaerp.open.dou;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DouRefundEmptyResultTest {
 @Test void emptySuccessfulWindowIsNotAnUpstreamFailure() {
  try(var platform=mockStatic(DouRefundApiHelper.class)) {
   platform.when(()->DouRefundApiHelper.pullAfterSaleListStr(1L,2L,1,100,1,"offline","offline","offline"))
    .thenReturn("{\"code\":10000,\"data\":{\"total\":0}}");
   platform.when(()->DouRefundApiHelper.pullAfterSaleList(1L,2L,1,100,"offline","offline","offline")).thenCallRealMethod();
   var result=DouRefundApiHelper.pullAfterSaleList(1L,2L,1,100,"offline","offline","offline");assertEquals(0,result.getCode());assertTrue(result.getList().isEmpty());
   platform.when(()->DouRefundApiHelper.pullAfterSaleListStr(1L,2L,1,100,1,"offline","offline","offline"))
    .thenReturn("{\"code\":10000,\"data\":{\"total\":-1}}");
   assertNotEquals(0,DouRefundApiHelper.pullAfterSaleList(1L,2L,1,100,"offline","offline","offline").getCode());
  }
 }
}
