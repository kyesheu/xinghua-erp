package cn.xinghuaerp.utils.http;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA 独立复核：通知 HTTP 客户端的加固是否真的落地（配置断言 + 响应体边界）。
 *
 * <p>配置类断言的价值在于「挡住将来有人顺手改回默认值」：OkHttp 默认
 * {@code retryOnConnectionFailure=true}、{@code followRedirects=true}、超时 10s，
 * 正是本次要修掉的姿态，所以这里逐项钉死。</p>
 */
class OkHttpClientHardeningQaTest {

    private static final int MAX_BYTES = 8 * 1024 * 1024;

    @Test
    @DisplayName("客户端配置：不自动重发 POST、不跟随重定向、整调用 30s 墙钟上限")
    void clientIsHardenedAgainstSilentRetryRedirectAndUnboundedCall() throws Exception {
        OkHttpClient client = singletonClient();

        assertFalse(client.retryOnConnectionFailure(),
                "retryOnConnectionFailure 必须为 false：连接层失败时自动重发 POST 发生在幂等闸门之后，会造成重复告警");
        assertFalse(client.followRedirects(), "不得跟随 3xx：webhook 的 3xx 只可能是配置错/劫持，跟随会把 token 转发到别处");
        assertFalse(client.followSslRedirects(), "不得跟随 SSL 3xx");
        assertEquals(30_000, client.callTimeoutMillis(), "callTimeout 必须是整次调用的墙钟上限，防慢速滴流占线程");
        assertEquals(5_000, client.connectTimeoutMillis(), "connectTimeout");
        assertEquals(20_000, client.readTimeoutMillis(), "readTimeout");
        assertEquals(20_000, client.writeTimeoutMillis(), "writeTimeout");
    }

    @Test
    @DisplayName("进程内只保留一个静态 final 客户端，不再每次调用新建 client")
    void onlyOneSharedClientIsDeclared() throws Exception {
        Field field = OkHttpClientHelper.class.getDeclaredField("client");
        int modifiers = field.getModifiers();
        assertTrue(Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers),
                "client 必须是 static final（每次 new 会重复建连、重复创建后台线程）");
        assertEquals(OkHttpClient.class, field.getType());

        long clientFields = Arrays.stream(OkHttpClientHelper.class.getDeclaredFields())
                .filter(f -> OkHttpClient.class.equals(f.getType()))
                .count();
        assertEquals(1, clientFields, "只允许一个 OkHttpClient 字段");
    }

    @Test
    @DisplayName("8MiB 边界：恰好等于上限接受，多 1 字节拒绝")
    void readsExactlyAtLimitAndRejectsOneByteOver() {
        byte[] exactlyAtLimit = new byte[MAX_BYTES];
        Arrays.fill(exactlyAtLimit, (byte) 'a');
        String accepted = assertDoesNotThrowIo(() -> OkHttpClientHelper.readBounded(
                ResponseBody.create(exactlyAtLimit, MediaType.get("application/json; charset=utf-8"))));
        assertEquals(MAX_BYTES, accepted.getBytes(StandardCharsets.UTF_8).length, "恰好 8MiB 必须被接受");

        byte[] oneByteOver = new byte[MAX_BYTES + 1];
        assertThrows(IOException.class, () -> OkHttpClientHelper.readBounded(
                        ResponseBody.create(oneByteOver, MediaType.get("application/json; charset=utf-8"))),
                "8MiB+1 必须拒绝");
    }

    @Test
    @DisplayName("声明长度超限时快速拒绝，一个字节都不从流里读")
    void rejectsDeclaredOversizeWithoutTouchingTheStream() {
        AtomicBoolean streamTouched = new AtomicBoolean(false);
        ResponseBody declaredOversize = new ResponseBody() {
            @Override
            public MediaType contentType() {
                return MediaType.get("application/json");
            }

            @Override
            public long contentLength() {
                return MAX_BYTES + 1L;
            }

            @Override
            public BufferedSource source() {
                streamTouched.set(true);
                return new Buffer();
            }
        };

        assertThrows(IOException.class, () -> OkHttpClientHelper.readBounded(declaredOversize));
        assertFalse(streamTouched.get(), "声明长度已超限却仍然去读流，快速拒绝没有生效");
    }

    @Test
    @DisplayName("声明长度撒谎（声小实大）时由读取上限兜住")
    void rejectsBodyThatLiesAboutItsLength() {
        ResponseBody lying = new ResponseBody() {
            @Override
            public MediaType contentType() {
                return MediaType.get("application/json");
            }

            @Override
            public long contentLength() {
                return 1024L; // 撒谎：实际远超上限
            }

            @Override
            public BufferedSource source() {
                byte[] payload = new byte[MAX_BYTES + 1];
                Arrays.fill(payload, (byte) 'b');
                return new Buffer().write(payload);
            }
        };

        assertThrows(IOException.class, () -> OkHttpClientHelper.readBounded(lying),
                "不声明长度/长度撒谎的响应必须被 readNBytes(limit+1) 兜住");
    }

    @Test
    @DisplayName("null 响应体（204/异常构造）抛 IOException 而不是 NPE")
    void nullBodyIsRejectedWithIoException() {
        IOException failure = assertThrows(IOException.class, () -> OkHttpClientHelper.readBounded(null));
        assertEquals("响应体为空", failure.getMessage());
    }

    @Test
    @DisplayName("UTF-8 中文不错码")
    void decodesChineseAsUtf8() {
        String text = "库存不足: 电饭煲 · 库存仅剩 1 件";
        String decoded = assertDoesNotThrowIo(() -> OkHttpClientHelper.readBounded(
                ResponseBody.create(text, MediaType.get("application/json; charset=utf-8"))));
        assertEquals(text, decoded);
    }

    @Test
    @DisplayName("按 Content-Type 声明的 charset 解码（GBK 渠道不乱码），无声明时回落 UTF-8")
    void decodesByDeclaredCharsetWithUtf8Fallback() {
        String text = "库存不足: 电饭煲";
        byte[] gbkBytes = text.getBytes(java.nio.charset.Charset.forName("GBK"));
        String decodedGbk = assertDoesNotThrowIo(() -> OkHttpClientHelper.readBounded(
                ResponseBody.create(gbkBytes, MediaType.get("application/json; charset=gbk"))));
        assertEquals(text, decodedGbk, "声明 GBK 时必须按 GBK 解码，否则中文乱码");

        byte[] utf8Bytes = text.getBytes(StandardCharsets.UTF_8);
        String decodedNoCharset = assertDoesNotThrowIo(() -> OkHttpClientHelper.readBounded(
                ResponseBody.create(utf8Bytes, MediaType.get("application/json"))));
        assertEquals(text, decodedNoCharset, "未声明 charset 时回落 UTF-8");
    }

    private static OkHttpClient singletonClient() throws Exception {
        Field field = OkHttpClientHelper.class.getDeclaredField("client");
        field.setAccessible(true);
        OkHttpClient client = (OkHttpClient) field.get(null);
        assertNotNull(client, "client 字段必须已初始化");
        return client;
    }

    private static String assertDoesNotThrowIo(IoSupplier supplier) {
        try {
            return supplier.get();
        } catch (IOException failure) {
            throw new AssertionError("不应抛出 IOException：" + failure.getMessage(), failure);
        }
    }

    @FunctionalInterface
    private interface IoSupplier {
        String get() throws IOException;
    }
}
