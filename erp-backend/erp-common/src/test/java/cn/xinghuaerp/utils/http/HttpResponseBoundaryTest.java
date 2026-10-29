package cn.xinghuaerp.utils.http;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 通知 HTTP 客户端的响应边界验证：按 charset 正确解码、空响应体拒绝、超 8MiB 拒绝。
 *
 * <p>与 erp-channel 的 {@code HttpResponseBoundaryTest} 同构（两个模块各自持有自己的客户端，
 * erp-common 不反向依赖 erp-channel，因此边界方法也各自保留一份实现）。</p>
 */
class HttpResponseBoundaryTest {

    @Test
    void acceptsUtf8AndRejectsOversizedAndMissingBodies() throws Exception {
        assertEquals("库存不足: 电饭煲",
                OkHttpClientHelper.readBounded(
                        ResponseBody.create("库存不足: 电饭煲", MediaType.get("application/json; charset=utf-8"))));

        // 声明长度 8MiB+1：走的是 contentLength() 快速拒绝分支（不是 readNBytes 的流式上限分支）
        assertThrows(IOException.class, () -> OkHttpClientHelper.readBounded(
                ResponseBody.create(new byte[8 * 1024 * 1024 + 1], null)));

        // 204/无响应体
        assertThrows(IOException.class, () -> OkHttpClientHelper.readBounded(null));
    }

    @Test
    void acceptsBodyExactlyAtLimit() throws Exception {
        byte[] payload = new byte[8 * 1024 * 1024];
        java.util.Arrays.fill(payload, (byte) 'a');

        String body = OkHttpClientHelper.readBounded(ResponseBody.create(payload, null));

        assertEquals(8 * 1024 * 1024, body.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void rejectsDeclaredContentLengthAboveLimitWithoutReading() {
        ResponseBody oversized = ResponseBody.create(new byte[8 * 1024 * 1024 + 2],
                MediaType.get("application/json; charset=utf-8"));

        assertThrows(IOException.class, () -> OkHttpClientHelper.readBounded(oversized));
    }

    /** 不声明长度（contentLength = -1）时，由 readNBytes(limit+1) 的流式上限兜住。 */
    @Test
    void rejectsOversizedBodyEvenWithoutDeclaredLength() {
        var buffer = new okio.Buffer().write(new byte[8 * 1024 * 1024 + 1]);
        ResponseBody unknownLength = ResponseBody.create(buffer, MediaType.get("application/json"), -1L);
        assertEquals(-1L, unknownLength.contentLength(), "用例前提：响应体不声明长度");

        assertThrows(IOException.class, () -> OkHttpClientHelper.readBounded(unknownLength));
    }

    /** 解码沿用 body.string() 的语义：按 Content-Type 的 charset 解，声明 GBK 就必须解成 GBK。 */
    @Test
    void respectsCharsetDeclaredInContentType() throws Exception {
        byte[] gbk = "库存不足".getBytes(java.nio.charset.Charset.forName("GBK"));
        // 前提：这批字节按 UTF-8 解不出原文，所以下面的断言只能来自「按 header 的 charset 解码」
        assertNotEquals("库存不足", new String(gbk, StandardCharsets.UTF_8));

        String decoded = OkHttpClientHelper.readBounded(
                ResponseBody.create(gbk, MediaType.get("application/json; charset=GBK")));

        assertEquals("库存不足", decoded);
    }

    @Test
    void fallsBackToUtf8WhenCharsetIsMissing() throws Exception {
        assertEquals("库存", OkHttpClientHelper.readBounded(
                ResponseBody.create("库存".getBytes(StandardCharsets.UTF_8), MediaType.get("application/json"))));
    }
}
