package cn.xinghuaerp.open.common;

import cn.xinghuaerp.open.jd.JdGoodsApiHelper;
import cn.xinghuaerp.open.jd.JdOrderApiHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「出口是否真的先问限流器」的行为测试（门禁级用例）。
 *
 * <p>为什么需要这个：{@code PlatformRateLimiterTest} 只测了限流器本身，
 * 它全绿也完全可能意味着「限流器写对了，但没有任何一条出口调用它」——
 * 也就是说那 18 处 {@code acquire} 如果被删掉，现有测试一条都不会红。
 *
 * <p>做法：把目标平台的桶打空 + 等待预算设为 0，然后调用真实出口方法。
 * 出口方法必须在<b>建立连接之前</b>抛「平台调用频率超限」；断言异常消息既证明
 * 「限流器被调用了」，也证明「抛在发请求之前」。
 *
 * <p>安全：全程不联网。URL 用 {@code http://api.jd.com:1/...}（端口 1，明文），
 * 即使限流被移除，最多是一次 TCP connect 被立即拒绝，不会有任何 HTTP 报文发到平台。
 */
class PlatformRateLimitWiringTest {

    /** 端口 1 上没有任何服务：TCP connect 会被立即拒绝，不会真的打到平台。 */
    private static final String JD_UNREACHABLE = "http://api.jd.com:1/routerjson";
    private static final String JD_URL = "https://api.jd.com/routerjson";

    @BeforeEach
    void resetBefore() {
        PlatformRateLimiter.resetForTest();
    }

    @AfterEach
    void resetAfter() {
        PlatformRateLimiter.resetForTest();
    }

    /** 把京东桶打空 + 预算归零：此后任何「识别出京东」的交流必然抛错。 */
    private static void exhaustJdQuota() throws IOException {
        PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.JD, 1));
        PlatformRateLimiter.acquire(JD_URL);          // 满桶只有 1 个令牌，用掉
        PlatformRateLimiter.setWaitBudgetMillis(0);   // 之后不再等待，直接拒绝
        assertThrows(IOException.class, () -> PlatformRateLimiter.acquire(JD_URL),
                "前置条件：京东桶必须已被打空且预算为 0，否则本用例什么也证明不了");
    }

    @Test
    void okHttpGetConsultsRateLimiterBeforeSending() throws IOException {
        exhaustJdQuota();
        IOException error = assertThrows(IOException.class, () -> OkHttpClientHelper.get(JD_UNREACHABLE));
        assertRateLimited(error);
    }

    @Test
    void okHttpPostConsultsRateLimiterBeforeSending() throws IOException {
        exhaustJdQuota();
        IOException error = assertThrows(IOException.class, () -> OkHttpClientHelper.post(JD_UNREACHABLE, "{}"));
        assertRateLimited(error);
    }

    @Test
    void okHttpPostFormConsultsRateLimiterBeforeSending() throws IOException {
        exhaustJdQuota();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", "jingdong.pop.order.enSearch");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        IOException error = assertThrows(IOException.class,
                () -> OkHttpClientHelper.postForm(JD_UNREACHABLE, params, headers));
        assertRateLimited(error);
    }

    @Test
    void boundedPlatformHttpConsultsRateLimiterBeforeSending() throws IOException {
        exhaustJdQuota();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(JD_UNREACHABLE)).GET().build();
        IOException error = assertThrows(IOException.class, () -> BoundedPlatformHttp.send(request));
        assertRateLimited(error);
    }

    /**
     * 京东订单分页被限流拒绝时必须返回错误码，而不是「成功 + 0 单」。
     *
     * <p><b>原用例名 {@code jdOrderPullTurnsRateLimitRejectionIntoEmptySuccess}，记录的是修复前的缺陷行为</b>：
     * 内层 {@code pullOrder(page=1)} 的 {@code catch (Exception e) { return ""; }} 把
     * {@code PlatformRateLimiter} 抛出的 IOException 吞成空串，分页壳再用
     * {@code if (StringUtils.hasText(result))} 判空跳过整块解析，最后
     * {@code ApiResultVo.success(0, emptyList)} —— 上层记录「成功，新增0」，操作员无从得知要重拉。
     *
     * <p>修复后翻转为正向断言：空响应 → 非 0 错误码 + 可读消息。走的是<b>真实出口</b>
     * （未替换取数入口），因此也顺带证明那处 {@code acquire} 仍在发请求之前生效。
     */
    @Test
    void jdOrderPullTurnsRateLimitRejectionIntoError() throws IOException {
        exhaustJdQuota();

        var result = JdOrderApiHelper.pullOrder(
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), "appKey", "appSecret", "accessToken");

        assertNotNull(result, "京东订单拉取在限流拒绝时不应返回 null");
        assertNotEquals(0, result.getCode(),
                "限流拒绝必须是非 0 错误码（修复前是 code=0，被上层记成「成功，新增0」）；msg=" + result.getMsg());
        assertNotNull(result.getMsg(), "失败必须带可读消息");
        assertTrue(result.getMsg().contains("无响应"),
                "消息应说明接口无响应（可能被限流或网络异常），便于操作员判断要重拉；实际：" + result.getMsg());
        assertTrue(result.getList() == null || result.getList().isEmpty(), "失败时不应带回任何订单");
    }

    /** 对照组：同样是京东，商品列表在空串时返回的是错误码，说明「吞异常」的严重程度按调用方而异。 */
    @Test
    void jdGoodsPullTurnsRateLimitRejectionIntoError() throws IOException {
        exhaustJdQuota();

        var result = JdGoodsApiHelper.pullGoodsSkuList("appKey", "appSecret", "accessToken");

        assertNotNull(result);
        assertTrue(result.getCode() != 0,
                "京东商品列表在空串时返回错误码（与订单链路不一致），实际 code=" + result.getCode()
                        + " msg=" + result.getMsg());
    }

    private static void assertRateLimited(IOException error) {
        assertNotNull(error.getMessage());
        assertTrue(error.getMessage().contains("平台调用频率超限"),
                "异常必须来自本地限流器而不是网络失败，实际消息：" + error.getMessage()
                        + "（若这里变成连接/解析错误，说明该出口没有调用 PlatformRateLimiter）");
    }
}
