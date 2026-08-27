package cn.xinghuaerp.open.common;

import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.dou.model.Token;
import cn.xinghuaerp.open.jd.response.JdOrderDetailResponse;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlatformJsonMigrationTest {
    @Test
    void readsSnakeCasePlatformFieldsAndUnknownExtensions() {
        Token token = JsonUtils.readValue("{\"access_token\":\"test-token\",\"refresh_token\":\"test-refresh\",\"shop_id\":123,\"expires_in\":7200,\"future_field\":true}", Token.class);
        assertEquals("test-token", token.getAccessToken());
        assertEquals("test-refresh", token.getRefreshToken());
        assertEquals(123, token.getShopId());
        assertEquals(7200, token.getExpiresIn());
    }

    @Test
    void readsJdResponseAndPreservesIdentifierStrings() {
        var response = JsonUtils.readTree("{\"orderInfo\":{\"orderId\":\"9007199254740993\",\"orderTotalPrice\":\"350.00\",\"future_field\":1}}");
        JdOrderDetailResponse order = JsonUtils.convert(response, "orderInfo", JdOrderDetailResponse.class);
        assertEquals("9007199254740993", order.getOrderId());
        assertEquals("350.00", order.getOrderTotalPrice());
    }

    @Test
    void preservesSignedRequestPayloadBytes() throws Exception {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("page", 1);
        payload.put("page_size", 10);
        payload.put("unused", null);
        String json = JsonUtils.write(payload);
        assertEquals("{\"page\":1,\"page_size\":10}", json);
        String sign = SignUtil.signTopRequest(Map.of("app_key", "test-app", "method", "test.method", "360buy_param_json", json), "test-secret");
        assertEquals("F4C8C974AD536FC9E42EE26E4923A7ED", sign);
    }
}
