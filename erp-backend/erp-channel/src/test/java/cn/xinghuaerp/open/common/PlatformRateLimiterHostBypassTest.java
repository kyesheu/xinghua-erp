package cn.xinghuaerp.open.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 独立复核「域名 → 平台」识别：既要挡住伪装域名，也不能把真平台 URL 漏判（漏判 = 限流被绕过）。
 *
 * <p>与 {@code PlatformRateLimiterTest} 的区别：那里是「按实现枚举」的正向断言，
 * 这里全部是<b>我自己构造的攻击/边界输入</b>，只断言外部可观察行为（返回 null 还是平台标识）。
 *
 * <p>关键性质：识别表只做 {@code URI#getHost()} 的精确匹配，因此
 * <ul>
 *   <li>「后缀伪装 / userinfo 伪装 / query 伪装」一定识别不出（否则限流会误伤他人域名）；</li>
 *   <li>反过来，只要 host 真的等于某个平台域名，就一定能识别出（否则调用方换了写法就绕过限流）。</li>
 * </ul>
 */
class PlatformRateLimiterHostBypassTest {

    @BeforeEach
    void resetBefore() {
        PlatformRateLimiter.resetForTest();
    }

    @AfterEach
    void resetAfter() {
        PlatformRateLimiter.resetForTest();
    }

    @Test
    void spoofedHostsAreNotRecognised() {
        // 后缀伪装：evil.cn 注册了 api.jd.com.evil.cn 这样的子域，不能被当成京东
        assertNull(PlatformRateLimiter.platformOf("https://api.jd.com.evil.cn/routerjson"),
                "后缀伪装域名不该被识别成京东");
        // userinfo 伪装：真正的 host 是 evil.cn，api.jd.com 只是 userinfo
        assertNull(PlatformRateLimiter.platformOf("https://api.jd.com@evil.cn/routerjson"),
                "userinfo 伪装域名不该被识别成京东");
        // query 伪装：host 是 evil.cn
        assertNull(PlatformRateLimiter.platformOf("https://evil.cn/?u=api.jd.com"),
                "query 里带平台域名不该被识别成京东");
        // 路径伪装
        assertNull(PlatformRateLimiter.platformOf("https://evil.cn/api.jd.com/routerjson"),
                "路径里带平台域名不该被识别成京东");
        // 子域前插：jd.com.evil.cn / notapi.jd.com 都不是表里的 host
        assertNull(PlatformRateLimiter.platformOf("https://notapi.jd.com/routerjson"),
                "未登记的子域不该被识别成京东（表里只有 api/open/open-oauth/api.jdl）");
    }

    @Test
    void realPlatformHostsAreRecognisedRegardlessOfWritingStyle() {
        // host 就是平台域名时，不同的写法都不能漏判，否则换一种写法就绕过了限流
        assertEquals(PlatformRateLimiter.JD, PlatformRateLimiter.platformOf("API.JD.COM"),
                "大写 + 无 scheme 也必须识别（否则写死大写的调用方绕过限流）");
        assertEquals(PlatformRateLimiter.JD, PlatformRateLimiter.platformOf("http://api.jd.com:8080/x"),
                "带端口必须识别（端口不影响归属）");
        assertEquals(PlatformRateLimiter.PDD, PlatformRateLimiter.platformOf("gw-api.pinduoduo.com."),
                "FQDN 末尾的点必须识别");
        assertEquals(PlatformRateLimiter.JD, PlatformRateLimiter.platformOf("https://api.jd.com./routerjson"),
                "带 scheme 的 FQDN 末尾点也必须识别");
        assertEquals(PlatformRateLimiter.JD, PlatformRateLimiter.platformOf("  HTTPS://API.JD.COM/routerjson  "),
                "前后空白 + 大写 scheme 必须识别");
        assertEquals(PlatformRateLimiter.TAO, PlatformRateLimiter.platformOf("http://api.taobao.com/router/rest?v=2"),
                "带 query 必须识别");
    }

    @Test
    void weirdInputsNeverThrow() {
        // 这些输入一旦抛异常，京东那 14 处 acquire 会被既有的 catch(Exception) 吞掉并返回空串，
        // 而 OkHttpClientHelper 的调用方则会直接失败 —— 所以「不抛异常」本身是行为契约
        assertDoesNotThrow(() -> PlatformRateLimiter.platformOf("https://[::1]/x"));
        assertDoesNotThrow(() -> PlatformRateLimiter.platformOf("https://[2001:db8::1]:8443/x"));
        assertDoesNotThrow(() -> PlatformRateLimiter.platformOf("file:///etc/passwd"));
        assertDoesNotThrow(() -> PlatformRateLimiter.platformOf("not a url"));
        assertDoesNotThrow(() -> PlatformRateLimiter.platformOf(null));
        assertDoesNotThrow(() -> PlatformRateLimiter.platformOf(""));
        assertDoesNotThrow(() -> PlatformRateLimiter.platformOf("   "));
        assertDoesNotThrow(() -> PlatformRateLimiter.platformOf("http://"));
        assertDoesNotThrow(() -> PlatformRateLimiter.platformOf("https://api.jd.com:99999999999999/x"));

        // IPv6 回环、file 协议、非法串一律不限流（返回 null）
        assertNull(PlatformRateLimiter.platformOf("https://[::1]/x"));
        assertNull(PlatformRateLimiter.platformOf("file:///etc/passwd"));
        assertNull(PlatformRateLimiter.platformOf("not a url"));
        assertNull(PlatformRateLimiter.platformOf(null));
        assertNull(PlatformRateLimiter.platformOf(""));
    }

    @Test
    void unrecognisedHostsAreReallyNotThrottled() throws IOException {
        // 不限流必须是「真的没走令牌桶」，而不是「识别成平台但恰好还有令牌」：
        // 先把京东/淘宝桶打空并把预算设为 0，再请求伪装域名，必须一条都不抛
        PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.JD, 1, PlatformRateLimiter.TAO, 1));
        PlatformRateLimiter.acquire("https://api.jd.com/routerjson");
        PlatformRateLimiter.acquire("https://api.taobao.com/router/rest");
        PlatformRateLimiter.setWaitBudgetMillis(0);
        assertThrows(IOException.class, () -> PlatformRateLimiter.acquire("https://api.jd.com/routerjson"),
                "前置条件：京东桶必须已被打空，否则本用例证明不了什么");

        String[] spoofed = {
                "https://api.jd.com.evil.cn/routerjson",
                "https://api.jd.com@evil.cn/routerjson",
                "https://evil.cn/?u=api.jd.com",
                "https://evil.cn/api.taobao.com/router/rest",
                "https://[::1]/x",
                "file:///etc/passwd",
                "not a url",
                "",
        };
        for (String url : spoofed) {
            assertDoesNotThrow(() -> PlatformRateLimiter.acquire(url),
                    "伪装/非法地址不该被限流（否则就是误伤）：" + url);
        }
        assertDoesNotThrow(() -> PlatformRateLimiter.acquire(null));
    }
}
