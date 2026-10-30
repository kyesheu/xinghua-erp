package cn.xinghuaerp.open.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlatformRateLimiter} 的行为测试。
 *
 * <p>关键约定：全部用例通过 {@code setNanoClock}/{@code setSleeper} 把「等待」变成「推进假时钟」，
 * 不真的 sleep 任何一秒；因此这些用例的总耗时必须是毫秒级（见各用例里的耗时断言）。
 */
class PlatformRateLimiterTest {

    private static final String DOU_URL = "https://openapi-fxg.jinritemai.com/order/searchList";
    private static final String TAO_URL = "https://api.taobao.com/router/rest";
    private static final String JD_URL = "https://api.jd.com/routerjson";

    /** 每个用例前后都恢复出厂状态：限流器是静态的，脏状态会串台。 */
    @BeforeEach
    void resetBefore() {
        PlatformRateLimiter.resetForTest();
    }

    @AfterEach
    void resetAfter() {
        PlatformRateLimiter.resetForTest();
    }

    @Test
    void platformIsRecognisedFromUrl() {
        assertEquals(PlatformRateLimiter.PDD, PlatformRateLimiter.platformOf("https://gw-api.pinduoduo.com/api/router"));
        assertEquals(PlatformRateLimiter.TAO, PlatformRateLimiter.platformOf("https://api.taobao.com/router/rest"));
        assertEquals(PlatformRateLimiter.JD, PlatformRateLimiter.platformOf("https://api.jd.com/routerjson"));
        assertEquals(PlatformRateLimiter.JD, PlatformRateLimiter.platformOf("https://open.jd.com/x"));
        assertEquals(PlatformRateLimiter.JD, PlatformRateLimiter.platformOf("https://open-oauth.jd.com/oauth2/authorize"));
        assertEquals(PlatformRateLimiter.JD, PlatformRateLimiter.platformOf("https://api.jdl.com/PullDataService/pullData"));
        assertEquals(PlatformRateLimiter.DOU, PlatformRateLimiter.platformOf("https://openapi-fxg.jinritemai.com/order/searchList"));
        assertEquals(PlatformRateLimiter.DOU, PlatformRateLimiter.platformOf("https://openapi.jinritemai.com/order/searchList"));
        assertEquals(PlatformRateLimiter.KWAI, PlatformRateLimiter.platformOf("https://openapi.kwaixiaodian.com/open/order/list"));
        assertEquals(PlatformRateLimiter.XHS, PlatformRateLimiter.platformOf("https://ark.xiaohongshu.com/ark/open_api/v3/common_controller"));
        assertEquals(PlatformRateLimiter.WEI, PlatformRateLimiter.platformOf("https://api.weixin.qq.com/cgi-bin/token"));
    }

    @Test
    void urlNoiseIsIgnoredWhenRecognisingPlatform() {
        // 端口、query、大小写、前后空白、绝对域名（FQDN）末尾的点，都不该影响归属判断
        assertEquals(PlatformRateLimiter.JD, PlatformRateLimiter.platformOf("https://API.JD.COM:443/routerjson?a=1&b=2"));
        assertEquals(PlatformRateLimiter.TAO, PlatformRateLimiter.platformOf("  HTTPS://Api.Taobao.Com/router/rest?sign=abc  "));
        assertEquals(PlatformRateLimiter.PDD, PlatformRateLimiter.platformOf("https://gw-api.pinduoduo.com.:8443/api/router"));
        // 缺 scheme 的写法也认（部分调用方直接拼 api.taobao.com/...）
        assertEquals(PlatformRateLimiter.TAO, PlatformRateLimiter.platformOf("api.taobao.com/router/rest"));
    }

    @Test
    void unknownHostIsNotThrottled() {
        // 仿冒域名、未知域名、非法 URL 一律不限流：宁可放过，也不能把本地/Mock/对象存储的调用挡在门外
        assertNull(PlatformRateLimiter.platformOf("https://api.jd.com.evil.cn/routerjson"));
        assertNull(PlatformRateLimiter.platformOf("https://oss.example.cn/bucket/a.json"));
        assertNull(PlatformRateLimiter.platformOf("https://api.jd.com@evil.cn/routerjson"));
        assertNull(PlatformRateLimiter.platformOf("not a url"));
        assertNull(PlatformRateLimiter.platformOf(""));
        assertNull(PlatformRateLimiter.platformOf(null));
        assertNull(PlatformRateLimiter.platformOf("http://127.0.0.1:8080/mock/order"));
    }

    @Test
    void acquireIsNoOpForUnknownHost() throws IOException {
        // 把抖店压到 1 次/秒并耗尽，保证「误判成抖店」会立刻抛错，从而证明未知域名确实走了旁路
        PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.DOU, 1));
        PlatformRateLimiter.setWaitBudgetMillis(1);
        PlatformRateLimiter.acquire(DOU_URL);
        assertThrows(IOException.class, () -> PlatformRateLimiter.acquire(DOU_URL));

        long startedAt = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            PlatformRateLimiter.acquire("https://oss.example.cn/bucket/a.json?x=1");
            PlatformRateLimiter.acquire("not a url");
            PlatformRateLimiter.acquire(null);
        }
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        assertTrue(elapsedMillis < 200, "未知域名不该产生任何等待，实际耗时 " + elapsedMillis + "ms");
    }

    @Test
    void acquireBlocksUntilTokenAvailable() throws IOException {
        FakeClock clock = new FakeClock();
        List<Long> sleptNanos = new ArrayList<>();
        PlatformRateLimiter.setNanoClock(clock::now);
        PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.DOU, 1));
        // 用「推进假时钟」替代真实 sleep：既验证等待时长算得对，又不让用例真的睡一秒
        PlatformRateLimiter.setSleeper(nanos -> {
            sleptNanos.add(nanos);
            clock.advance(nanos);
        });

        long startedAt = System.nanoTime();
        PlatformRateLimiter.acquire(DOU_URL);
        assertEquals(0, sleptNanos.size(), "初始是满桶，第一次调用应当直接放行");

        PlatformRateLimiter.acquire(DOU_URL);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(1, sleptNanos.size(), "qps=1 时第二次调用必须等令牌补充");
        assertEquals(1_000_000_000L, sleptNanos.get(0), "等待时长应约等于 1 个补充周期（1 秒）");
        assertEquals(2_000_000_000L, clock.now(), "等待后时钟应被推进约 1 秒");
        assertTrue(elapsedMillis < 200, "用例本身不该真实等待，实际耗时 " + elapsedMillis + "ms");
    }

    @Test
    void acquireThrowsWhenWaitBudgetExhausted() throws IOException {
        FakeClock clock = new FakeClock();
        PlatformRateLimiter.setNanoClock(clock::now);
        PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.DOU, 1));
        PlatformRateLimiter.setWaitBudgetMillis(5);
        // 等待实现不推进时钟：模拟「预算被瞬间判定不足」，此时不该有任何真实等待
        PlatformRateLimiter.setSleeper(nanos -> {
            throw new AssertionError("预算已耗尽，不应该再进入等待：期望 " + nanos);
        });

        PlatformRateLimiter.acquire(DOU_URL);
        IOException error = assertThrows(IOException.class, () -> PlatformRateLimiter.acquire(DOU_URL));
        assertTrue(error.getMessage().contains("平台调用频率超限"), error.getMessage());
        assertTrue(error.getMessage().contains(PlatformRateLimiter.DOU), "异常信息必须带平台名：" + error.getMessage());
        assertTrue(error.getMessage().contains("超出 1 次/秒"), "异常信息必须带生效的 QPS：" + error.getMessage());
    }

    @Test
    void capacityEqualsConfiguredQpsAndThenWaits() throws IOException {
        FakeClock clock = new FakeClock();
        AtomicInteger waits = new AtomicInteger();
        PlatformRateLimiter.setNanoClock(clock::now);
        PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.JD, 3));
        PlatformRateLimiter.setSleeper(nanos -> {
            waits.incrementAndGet();
            clock.advance(nanos);
        });

        for (int i = 0; i < 3; i++) {
            PlatformRateLimiter.acquire(JD_URL);
        }
        assertEquals(0, waits.get(), "桶容量应等于配置的 QPS（3），前 3 次是允许的突发");

        PlatformRateLimiter.acquire(JD_URL);
        assertEquals(1, waits.get(), "第 4 次必须等待令牌补充");
    }

    @Test
    void eachPlatformHasIndependentBucket() throws IOException {
        FakeClock clock = new FakeClock();
        AtomicInteger waits = new AtomicInteger();
        PlatformRateLimiter.setNanoClock(clock::now);
        PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.DOU, 1, PlatformRateLimiter.TAO, 1));
        PlatformRateLimiter.setSleeper(nanos -> {
            waits.incrementAndGet();
            clock.advance(nanos);
        });

        PlatformRateLimiter.acquire(DOU_URL);
        PlatformRateLimiter.acquire(DOU_URL);
        assertEquals(1, waits.get(), "抖店用完之后自己等，不应影响别的平台");

        // 淘宝桶此时仍是满的：若两个平台共用桶，这里会被迫再等一次
        PlatformRateLimiter.acquire(TAO_URL);
        assertEquals(1, waits.get(), "淘宝有自己的桶，不该因为抖店限额而排队");
    }

    @Test
    void configureIgnoresInvalidValues() {
        // 非法配置（null / 0 / 负数 / 空白 key）被忽略，不会把平台限成「0 次/秒」导致业务直接不可用
        assertDoesNotThrow(() -> PlatformRateLimiter.configure(null));
        assertDoesNotThrow(() -> PlatformRateLimiter.configure(Map.of()));
        assertDoesNotThrow(() -> PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.DOU, 0)));
        assertDoesNotThrow(() -> PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.DOU, -5)));
        assertDoesNotThrow(() -> PlatformRateLimiter.configure(Map.of(" ", 10)));
        assertDoesNotThrow(() -> PlatformRateLimiter.acquire(DOU_URL));
    }

    @Test
    void configurationIsWiredIntoLimiter() throws IOException {
        // 直接构造配置类，验证「键 → 平台 → 桶容量」的映射没有错位（例如把 dou 的值塞进 kwai）。
        // 8 个参数依次是 pdd/tao/jd/dou/kwai/xhs/wei/waitMillis。
        FakeClock clock = new FakeClock();
        AtomicInteger waits = new AtomicInteger();
        PlatformRateLimiter.setNanoClock(clock::now);
        PlatformRateLimiter.setSleeper(nanos -> {
            waits.incrementAndGet();
            clock.advance(nanos);
        });
        new PlatformRateLimitConfig(1, 2, 3, 4, 5, 6, 7, 1000);

        assertEquals(1, drainAndCountWait(PlatformRateLimiter.PDD,
                "https://gw-api.pinduoduo.com/api/router", 1, waits), "pdd 容量应为 1");
        assertEquals(1, drainAndCountWait(PlatformRateLimiter.TAO, TAO_URL, 2, waits), "tao 容量应为 2");
        assertEquals(1, drainAndCountWait(PlatformRateLimiter.JD, JD_URL, 3, waits), "jd 容量应为 3");
        assertEquals(1, drainAndCountWait(PlatformRateLimiter.DOU, DOU_URL, 4, waits), "dou 容量应为 4");
        assertEquals(1, drainAndCountWait(PlatformRateLimiter.KWAI,
                "https://openapi.kwaixiaodian.com/open/order/list", 5, waits), "kwai 容量应为 5");
        assertEquals(1, drainAndCountWait(PlatformRateLimiter.XHS,
                "https://ark.xiaohongshu.com/ark/open_api/v3/common_controller", 6, waits), "xhs 容量应为 6");
        assertEquals(1, drainAndCountWait(PlatformRateLimiter.WEI,
                "https://api.weixin.qq.com/cgi-bin/token", 7, waits), "wei 容量应为 7");
        assertEquals(PlatformRateLimiter.WEI, PlatformRateLimiter.platformOf("https://api.weixin.qq.com/cgi-bin/token"));
    }

    /** 连续取 {@code capacity} 次（不应等待），再取一次（应当等待），返回本次新增的等待次数。 */
    private static int drainAndCountWait(String platform, String url, int capacity, AtomicInteger waits) throws IOException {
        int before = waits.get();
        for (int i = 0; i < capacity; i++) {
            PlatformRateLimiter.acquire(url);
        }
        int afterBurst = waits.get();
        assertEquals(before, afterBurst, platform + " 的桶容量应至少为 " + capacity);
        PlatformRateLimiter.acquire(url);
        return waits.get() - before;
    }

    /** 可推进的假时钟（纳秒），起点取非零值以便发现「没初始化就被用」的问题。 */
    private static final class FakeClock {

        private long nanos = 1_000_000_000L;

        long now() {
            return nanos;
        }

        void advance(long delta) {
            nanos += delta;
        }
    }
}
