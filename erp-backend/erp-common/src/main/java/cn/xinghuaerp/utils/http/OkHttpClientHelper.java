package cn.xinghuaerp.utils.http;

import cn.xinghuaerp.json.JsonUtils;
import tools.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 业务侧（消息通知等）对外 HTTP 调用的统一客户端。
 *
 * <p>本类只保留<b>一个</b>进程内单例 {@link OkHttpClient}：OkHttp 自带连接池与 Dispatcher，
 * 每次调用新建 client 会反复建连、反复创建后台线程，长跑必然耗尽连接与线程。</p>
 *
 * @author qi
 */
public class OkHttpClientHelper {

    /** 响应体读取上限 8MiB：异常渠道返回超大响应体时读到上限即放弃，避免把堆吃满。 */
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    /**
     * 单例 client，超时/重试/重定向策略与 erp-channel 的平台客户端保持一致，避免两处漂移。
     *
     * <p><b>为什么 {@code retryOnConnectionFailure} 必须关掉：</b>OkHttp 默认是 true，连接层失败
     * （连接被重置、连接池里的复用连接已失效等）时它会自动重发这个 POST。重发发生在应用层
     * {@code claimNotify} 幂等闸门<b>之后</b>——闸门已经放行、上层只发起了一次调用，传输层却偷偷
     * 重放了一次，应用层既看不到也拦不住。钉钉/飞书/企微的 webhook 不做消息幂等去重，
     * 用户就会收到两条重复告警。宁可让这次投递失败、交给下一轮补偿按业务幂等重试，
     * 也不允许传输层重放。</p>
     *
     * <p><b>为什么需要 {@code callTimeout}：</b>只设 connect/read/write 时，一个「慢速滴流」的响应
     * 只要每个分包间隔小于 readTimeout 就一直不会超时，单次调用能占用线程远超预期；
     * callTimeout(30s) 是整次调用的墙钟上限，保证单渠道最坏耗时有界。</p>
     *
     * <p><b>为什么不跟随重定向：</b>通知地址是配置死的 webhook，出现 3xx 只可能是地址填错、网关
     * 劫持或域名过期。跟随重定向会把带 token 的请求体转发到别处，属于凭据外泄。</p>
     */
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private static final ObjectMapper objectMapper = JsonUtils.mapper();

    private OkHttpClientHelper() {
    }

    // 执行 GET 请求
    public static String get(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP状态异常：" + response.code());
            return readBounded(response.body());
        }
    }

    // 执行 POST 请求
    public static String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP状态异常：" + response.code());
            return readBounded(response.body());
        }
    }

    /**
     * 带请求头的 JSON POST。400/403 以字符串返回的约定保持不变（调用方可能把它当字符串判断）。
     */
    public static String post(String url, Map<String, String> headers, String json) throws IOException {
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        // 构建请求头
        Headers.Builder headersBuilder = new Headers.Builder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            headersBuilder.add(entry.getKey(), entry.getValue());
        }
        Headers header = headersBuilder.build();

        Request request = new Request.Builder()
                .url(url)
                .headers(header)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(response.code()==400) return "400";
            else if (response.code()==403) return "403";
            if (!response.isSuccessful()) throw new IOException("HTTP状态异常：" + response.code());
            return readBounded(response.body());
        }
    }

    /**
     * 带请求头的 JSON POST（请求体由 Map 序列化）。400/403 以字符串返回的约定保持不变。
     */
    public static String post(String url, Map<String, String> headers, Map<String, Object> requestBody) throws IOException {
        // 将Map转换为JSON字符串
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        // 构建请求体
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        // 构建请求头
        Headers.Builder headersBuilder = new Headers.Builder();
        if(headers!=null&&headers.size()>0) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                headersBuilder.add(entry.getKey(), entry.getValue());
            }
        }
        Headers header = headersBuilder.build();

        Request request = new Request.Builder()
                .url(url)
                .headers(header)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(response.code()==400) return "400";
            else if (response.code()==403) return "403";
            if (!response.isSuccessful()) throw new IOException("HTTP状态异常：" + response.code());
            return readBounded(response.body());
        }
    }

    /**
     * 有上限的响应体读取，按响应头声明的 charset 解码（取不到回落 UTF-8）。
     *
     * <p>不带上限的 {@code body.string()} 会把对端返回的任何大小都读进堆：一个异常/被劫持的
     * 渠道返回几百 MB 就能把进程 OOM。这里先看 Content-Length 快速拒绝，再用
     * {@code readNBytes(limit+1)} 兜住「不声明长度或长度撒谎」的响应。</p>
     *
     * <p>解码沿用 {@code body.string()} 的语义：优先用 {@code Content-Type} 里的 charset，
     * 缺失时回落 UTF-8。原来写死 UTF-8 会把声明了非 UTF-8 charset 的响应解成乱码，
     * 属于没必要的退化。</p>
     *
     * @param body 响应体，可为 null（204 或异常构造的响应）
     * @return 解码后的响应文本
     * @throws IOException 响应体为空或超过上限
     */
    static String readBounded(ResponseBody body) throws IOException {
        if (body == null) throw new IOException("响应体为空");
        if (body.contentLength() > MAX_RESPONSE_BYTES) throw new IOException("响应超过8MiB，已放弃读取");
        try (var stream = body.byteStream()) {
            byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("响应超过8MiB，已放弃读取");
            MediaType contentType = body.contentType();
            Charset charset = contentType == null ? null : contentType.charset(StandardCharsets.UTF_8);
            return new String(bytes, charset == null ? StandardCharsets.UTF_8 : charset);
        }
    }
}
