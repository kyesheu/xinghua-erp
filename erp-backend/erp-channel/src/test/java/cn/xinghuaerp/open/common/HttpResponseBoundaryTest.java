package cn.xinghuaerp.open.common;
import org.junit.jupiter.api.Test;
import okhttp3.*;
import static org.junit.jupiter.api.Assertions.*;
class HttpResponseBoundaryTest {
 @Test void acceptsUtf8AndRejectsOversizedAndMissingBodies() throws Exception {
  assertEquals("库存",OkHttpClientHelper.readBounded(ResponseBody.create("库存",MediaType.get("application/json; charset=utf-8"))));
  assertThrows(java.io.IOException.class,()->OkHttpClientHelper.readBounded(null));
  assertThrows(java.io.IOException.class,()->OkHttpClientHelper.readBounded(ResponseBody.create(new byte[8*1024*1024+1],null)));
 }
}
