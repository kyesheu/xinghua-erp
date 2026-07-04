package cn.xinghuaerp.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class JsonUtilsTest {
    record Item(@JsonProperty("sku_id") String skuId, int quantity) { }

    @Test
    void readsNestedPayloadsAndEncodedJson() {
        JsonNode root = JsonUtils.readTree("""
                {"code":"0","data":{"items":[{"sku_id":"9007199254740993","quantity":2,"extra":1}]},
                 "encoded":"{\\\"sku_id\\\":\\\"B\\\",\\\"quantity\\\":3}"}
                """);
        assertEquals(0, JsonUtils.integer(root, "code"));
        var items = JsonUtils.list(JsonUtils.node(root, "data"), "items", Item.class);
        assertEquals("9007199254740993", items.get(0).skuId());
        assertEquals(2, items.get(0).quantity());
        assertEquals("B", JsonUtils.convert(root, "encoded", Item.class).skuId());
        assertEquals("B", JsonUtils.text(JsonUtils.node(root, "encoded"), "sku_id"));
        assertTrue(JsonUtils.text(root, "data").startsWith("{"));
    }

    @Test
    void preservesNullAndDefaultSemantics() {
        JsonNode root = JsonUtils.readTree("{\"value\":null,\"zero\":0,\"empty\":\"\"}");
        assertNull(JsonUtils.node(root, "missing"));
        assertNull(JsonUtils.node(root, "value"));
        assertNull(JsonUtils.integer(root, "empty"));
        assertNull(JsonUtils.integer(root, "missing"));
        assertEquals(0, JsonUtils.intOrZero(root, "missing"));
        assertEquals(5, JsonUtils.intOrZero(root, "missing", 5));
        assertEquals(0, JsonUtils.intOrZero(root, "zero", 5));
        assertNull(JsonUtils.readTree(" "));
    }

    @Test
    void preservesRequestParameterOrderAndOmitsNulls() {
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("start_time", "2026-10-26 00:00:00");
        parameters.put("page", 1);
        parameters.put("amount", new BigDecimal("20.30"));
        parameters.put("unused", null);
        String expected = "{\"start_time\":\"2026-10-26 00:00:00\",\"page\":1,\"amount\":20.30}";
        assertEquals(expected, JsonUtils.write(parameters));
    }

    @Test
    void preservesLargeIdsAndScalarArrayValues() {
        JsonNode values = JsonUtils.readTree("[\"9007199254740993\",9007199254740993]");
        assertEquals("9007199254740993", JsonUtils.text(values.get(0)));
        assertEquals("9007199254740993", JsonUtils.text(values.get(1)));
        assertEquals(new BigDecimal("123456789.123456789"),
                JsonUtils.decimal(JsonUtils.readTree("{\"amount\":\"123456789.123456789\"}"), "amount"));
        assertEquals(new BigDecimal("123456789.123456789"),
                JsonUtils.decimal(JsonUtils.readTree("{\"amount\":123456789.123456789}"), "amount"));
    }

    @Test
    void preservesPlatformDateFormatAndAcceptsBothTimestampStrings() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 26, 10, 30);
        assertEquals("\"2026-10-26 10:30:00\"", JsonUtils.write(timestamp));
        assertEquals(timestamp, JsonUtils.readValue("\"2026-10-26 10:30:00\"", LocalDateTime.class));
        assertEquals(timestamp, JsonUtils.readValue("\"2026-10-26T10:30:00\"", LocalDateTime.class));
    }

    @Test
    void ignoresTypeMetadataInExternalPayloads() {
        Item item = JsonUtils.readValue("{\"@type\":\"java.lang.Runtime\",\"sku_id\":\"A\",\"quantity\":1}", Item.class);
        assertEquals("A", item.skuId());
        assertEquals(1, item.quantity());
    }
}
