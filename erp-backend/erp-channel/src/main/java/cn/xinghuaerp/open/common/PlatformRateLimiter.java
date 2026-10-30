package cn.xinghuaerp.open.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/**
 * 外部平台 API 调用的进程内限流器（按平台维度）。
 *
 * <p><b>为什么按「平台」而不是按「接口 URL」限流。</b>四家平台的限流维度根本不同：
 * <ul>
 *   <li>抖店：按「单应用」维度限流，但每个接口的值不同（{@code /order/searchList} 1000 次/秒、
 *       {@code /order/addOrderRemark} 200 次/秒、{@code /afterSale/List} 500 次/秒、
 *       {@code /order/batchDecrypt} 600 次/秒），另有所有应用共享的「接口总限流」（数值不公开）。
 *       见 https://op.jinritemai.com/docs/api/114/2158</li>
 *   <li>淘宝：官方只给每 appkey 的「每日调用总量」（如商家后台系统 100 万次/天，测试状态 5000 次/天），
 *       没有公开每 appkey 的 QPS 承诺；接口级 QPS 是所有 ISV 共享的平台级限制，
 *       报错 {@code accesscontrol.limited-by-api-access-count}。
 *       见 https://open.taobao.com/docV3.htm?docId=103492&amp;docType=1 、
 *       https://open.taobao.com/help?docId=6&amp;docType=14</li>
 *   <li>拼多多：官方只公开解密接口 1 次/10 秒（云外），普通接口的 3000 次/60 秒来自第三方资料。</li>
 *   <li>京东：官方未公开 QPS，第三方资料称个人 1~2/s、企业未买包 5/s、买资源包 20~100/s。</li>
 * </ul>
 * 我们既拿不到接口级配额，也无法在客户端准确建模平台侧的共享限流，只能在「平台」这一层做保守的
 * 自我保护：防止多店铺/多线程并发拉取叠加后把配额一次性打满，导致整批任务被平台封一段时间。
 *
 * <p><b>任何写死的数字都不是平台承诺。</b>本类的默认值只是保守兜底，必须按各自证书/权限包的实际配额
 * 覆盖（见 {@code erp-api} 的 {@code application.yml} 中 {@code erp.platform.rate-limit.*}）。
 *
 * <p><b>边界（务必读）。</b>这里是<b>单实例进程内</b>限流：多实例部署时实际总速率 = 实例数 × 配置值，
 * 不是全局配额；要全局配额必须换成 Redis/库表令牌桶。不要把本类理解为「已实现平台配额保护」。
 *
 * <p><b>为什么用静态 API。</b>平台调用方全是 {@code *ApiHelper} 之类静态工具类，没有 Spring 容器上下文，
 * 无法注入 Bean；配置由 {@code PlatformRateLimitConfig} 在启动时一次性灌进来。
 *
 * <p><b>为什么超限抛 {@link IOException}。</b>上层所有平台调用方法本来就声明了 {@code throws IOException}
 * 或已被宽泛的 {@code catch (Exception)} 兜住，抛 IOException 不改变任何既有签名与 catch 结构。
 */
public final class PlatformRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(PlatformRateLimiter.class);

    /** 拼多多。域名 {@code gw-api.pinduoduo.com}。 */
    static final String PDD = "pdd";
    /** 淘宝/天猫开放平台。域名 {@code api.taobao.com}。 */
    static final String TAO = "tao";
    /** 京东（含京东物流 api.jdl.com）。域名 {@code api.jd.com} / {@code open.jd.com} / {@code open-oauth.jd.com} / {@code api.jdl.com}。 */
    static final String JD = "jd";
    /** 抖店（抖音电商）。域名 {@code openapi-fxg.jinritemai.com} / {@code openapi.jinritemai.com}。 */
    static final String DOU = "dou";
    /** 快手小店。域名 {@code openapi.kwaixiaodian.com}。 */
    static final String KWAI = "kwai";
    /** 小红书。域名 {@code ark.xiaohongshu.com}。 */
    static final String XHS = "xhs";
    /** 微信（公众号/小程序）。域名 {@code api.weixin.qq.com}。 */
    static final String WEI = "wei";

    /**
     * 域名 → 平台的私有映射。
     *
     * <p>只做精确匹配（{@code URI#getHost()} 归一化后比较），不做「包含」匹配：
     * 否则 {@code api.jd.com.evil.cn} 这类域也会被当成京东，限流就成了误伤。
     * 未登记域名一律<b>不限流</b>——本地 Mock、对象存储、第三方物流等不该被顺带节流。
     */
    private static final Map<String, String> HOST_PLATFORM = Map.ofEntries(
            Map.entry("gw-api.pinduoduo.com", PDD),
            Map.entry("api.taobao.com", TAO),
            Map.entry("api.jd.com", JD),
            Map.entry("open.jd.com", JD),
            Map.entry("open-oauth.jd.com", JD),
            // 京东物流（云打印 PullDataService）走 api.jdl.com：它和京东零售的配额是分开计量的，
            // 合并进同一个桶只会更保守（不会更宽松），对「自我保护」来说是可接受的方向。
            Map.entry("api.jdl.com", JD),
            Map.entry("openapi-fxg.jinritemai.com", DOU),
            Map.entry("openapi.jinritemai.com", DOU),
            Map.entry("openapi.kwaixiaodian.com", KWAI),
            Map.entry("ark.xiaohongshu.com", XHS),
            Map.entry("api.weixin.qq.com", WEI));

    /**
     * 默认 QPS（每平台一个令牌桶，容量 = QPS，即允许 1 秒的突发）。
     *
     * <p>逐条说明出处，并请记住<b>它们都不是平台官方承诺的值</b>：
     * <ul>
     *   <li>{@code dou}=20：抖店官方最低的常用接口也有 100 次/秒以上，20 极保守，
     *       主要防的是「多店铺并发叠加」而不是单接口超限。</li>
     *   <li>{@code tao}=10：淘宝官方只有日配额（100 万次/天 ≈ 11.6 次/秒），取 10 留余量。</li>
     *   <li>{@code pdd}=10：社区资料的 50 次/秒并非官方承诺，取 1/5 保守。</li>
     *   <li>{@code jd}=5：社区资料「企业未买资源包 5 次/秒」。</li>
     *   <li>{@code kwai}/{@code xhs}/{@code wei}=10：官方无公开配额，保守兜底。</li>
     * </ul>
     * 按实际证书配额覆盖配置，不要照抄。
     */
    private static final Map<String, Integer> DEFAULT_QPS = Map.of(
            PDD, 10,
            TAO, 10,
            JD, 5,
            DOU, 20,
            KWAI, 10,
            XHS, 10,
            WEI, 10);

    /** 默认单次调用最多愿意等多久：超过就快速失败，让上层任务按自己的重试策略退避。 */
    private static final long DEFAULT_WAIT_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(1000);

    /** 每个平台一个桶；只在首次用到时创建，桶数 = 平台数，规模可忽略。 */
    private static final ConcurrentHashMap<String, Bucket> BUCKETS = new ConcurrentHashMap<>();

    /** 平台 → QPS。用 volatile 保证 configure 后所有线程立即看到新值。 */
    private static volatile Map<String, Integer> qpsByPlatform = Map.copyOf(DEFAULT_QPS);

    /** 单次 {@link #acquire(String)} 允许累计等待的时长。 */
    private static volatile long waitBudgetNanos = DEFAULT_WAIT_BUDGET_NANOS;

    /** 单调时钟；抽出来是为了让测试用「推进时间」代替真实 sleep。 */
    private static volatile LongSupplier nanoClock = System::nanoTime;

    /** 等待实现；抽出来是为了让测试不真的睡满一秒。默认 park（不占 CPU、响应中断）。 */
    private static volatile NanoSleeper sleeper = LockSupport::parkNanos;

    private PlatformRateLimiter() {
    }

    /**
     * 申请一次平台调用许可：拿到令牌立即返回，拿不到就在等待预算内等待，预算耗尽则抛异常。
     *
     * @param url 目标地址；平台由域名识别，识别不出（本地/Mock/对象存储/非法 URL）则不限流，直接返回
     * @throws IOException 等待预算耗尽仍未拿到令牌，或等待期间线程被中断
     */
    public static void acquire(String url) throws IOException {
        String platform = platformOf(url);
        if (platform == null) {
            return;
        }
        Bucket bucket = bucketOf(platform);
        if (bucket == null) {
            // 该平台被配置成非法值（<=0）且没有默认值兜底：宁可不限流，也不能让业务调不通。
            return;
        }
        long remaining = waitBudgetNanos;
        while (true) {
            Reservation reservation = bucket.reserve(nanoClock.getAsLong(), remaining);
            switch (reservation.decision()) {
                case GRANTED -> {
                    return;
                }
                case BUDGET_EXCEEDED -> throw overLimit(platform, bucket.qps());
                case WAIT -> {
                    if (Thread.currentThread().isInterrupted()) {
                        // 被取消的拉取任务不该继续排队等配额，否则线程池迟迟收不回来。
                        throw new IOException("平台调用频率超限：" + platform + " 等待令牌时线程被中断，请稍后重试");
                    }
                    remaining -= reservation.waitNanos();
                    sleeper.sleepNanos(reservation.waitNanos());
                }
            }
        }
    }

    /**
     * 用配置覆盖各平台 QPS（不传的平台继续用默认值）。
     *
     * <p>会清空现有桶：本方法只在启动装配阶段调用一次，清空可避免旧时钟/旧速率残留。
     *
     * @param qpsByPlatform 平台 → QPS；key 大小写不敏感，value 为 null 或 &lt; 1 的条目被忽略
     */
    public static void configure(Map<String, Integer> qpsByPlatform) {
        if (qpsByPlatform == null || qpsByPlatform.isEmpty()) {
            return;
        }
        Map<String, Integer> merged = new LinkedHashMap<>(DEFAULT_QPS);
        qpsByPlatform.forEach((platform, qps) -> {
            if (platform == null || platform.isBlank() || qps == null || qps < 1) {
                return;
            }
            merged.put(platform.trim().toLowerCase(Locale.ROOT), qps);
        });
        PlatformRateLimiter.qpsByPlatform = Map.copyOf(merged);
        BUCKETS.clear();
    }

    /**
     * 识别 URL 所属平台。
     *
     * <p>包级可见：既给 {@link #acquire(String)} 用，也让单元测试能直接断言映射结果。
     *
     * @param url 目标地址
     * @return 平台标识；无法识别时返回 {@code null}（表示不限流）
     */
    static String platformOf(String url) {
        String host = hostOf(url);
        return host == null ? null : HOST_PLATFORM.get(host);
    }

    /** 等待预算（毫秒），包级可见供配置类与测试使用。 */
    static void setWaitBudgetMillis(long millis) {
        waitBudgetNanos = millis <= 0 ? 0 : TimeUnit.MILLISECONDS.toNanos(millis);
    }

    /** 注入时钟，测试专用。 */
    static void setNanoClock(LongSupplier clock) {
        nanoClock = clock == null ? System::nanoTime : clock;
    }

    /** 注入等待实现，测试专用。 */
    static void setSleeper(NanoSleeper newSleeper) {
        sleeper = newSleeper == null ? LockSupport::parkNanos : newSleeper;
    }

    /** 恢复出厂状态（默认 QPS、默认预算、真实时钟、真实 park），测试专用。 */
    static void resetForTest() {
        qpsByPlatform = Map.copyOf(DEFAULT_QPS);
        waitBudgetNanos = DEFAULT_WAIT_BUDGET_NANOS;
        nanoClock = System::nanoTime;
        sleeper = LockSupport::parkNanos;
        BUCKETS.clear();
    }

    /**
     * 从 URL 里取出归一化后的主机名。
     *
     * <p>用 {@link URI#getHost()} 而不是字符串 contains：后者会被 {@code ?redirect=api.jd.com}、
     * {@code api.jd.com.evil.cn} 这类输入骗过。取不到 host 时补一次 {@code https://} 前缀重试，
     * 兼容调用方只传 {@code api.taobao.com/router/rest} 的写法。
     */
    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        String host = parseHost(trimmed);
        if (host == null) {
            host = parseHost("https://" + trimmed);
        }
        if (host == null || host.isEmpty()) {
            return null;
        }
        // 绝对域名写法（FQDN）末尾的点不影响归属，剥掉再查表。
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static String parseHost(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            // URL 非法（缺 scheme、含非法字符等）：判不出平台，按不限流处理，避免把业务挡在门外。
            return null;
        }
    }

    private static Bucket bucketOf(String platform) {
        Integer qps = qpsByPlatform.get(platform);
        if (qps == null || qps < 1) {
            return null;
        }
        // 只有 QPS 变化时才重建桶；正常情况下 get 即可，避免每条请求都进 compute 锁。
        Bucket existing = BUCKETS.get(platform);
        if (existing != null && existing.qps() == qps) {
            return existing;
        }
        return BUCKETS.compute(platform, (key, current) ->
                current != null && current.qps() == qps ? current : new Bucket(qps, nanoClock.getAsLong()));
    }

    private static IOException overLimit(String platform, int qps) {
        log.warn("平台调用被本地限流拦截：platform={}, qps={}，已达到单实例等待上限", platform, qps);
        return new IOException("平台调用频率超限：" + platform + " 超出 " + qps + " 次/秒，请稍后重试");
    }

    /** 等待实现（纳秒）。默认 park，测试可替换成「推进假时钟」。 */
    @FunctionalInterface
    interface NanoSleeper {
        void sleepNanos(long nanos);
    }

    /** 令牌结算结果。 */
    private record Reservation(Decision decision, long waitNanos) {

        static Reservation granted() {
            return new Reservation(Decision.GRANTED, 0L);
        }

        static Reservation waiting(long waitNanos) {
            return new Reservation(Decision.WAIT, waitNanos);
        }

        static Reservation exceeded() {
            return new Reservation(Decision.BUDGET_EXCEEDED, 0L);
        }
    }

    private enum Decision {
        /** 已扣减一个令牌，可以发出请求。 */
        GRANTED,
        /** 令牌不足，但缺口在等待预算内，等 {@code waitNanos} 后再试。 */
        WAIT,
        /** 令牌缺口超过剩余等待预算，快速失败。 */
        BUDGET_EXCEEDED
    }

    /**
     * 单平台令牌桶：容量 = QPS（允许 1 秒突发），以 QPS/秒 匀速补充。
     *
     * <p>用 {@code double} 存令牌，保留「不足一个令牌」的补充精度，否则低 QPS 时补充会被反复截断、
     * 实际速率明显低于配置值。
     */
    private static final class Bucket {

        private final int qps;
        private double tokens;
        private long lastNanos;

        Bucket(int qps, long now) {
            this.qps = qps;
            // 初始满桶：进程刚启动时允许一次性突发 1 秒的量，避免首批请求互相排队。
            this.tokens = qps;
            this.lastNanos = now;
        }

        int qps() {
            return qps;
        }

        /**
         * 尝试结算一个令牌。方法内不加任何 sleep，避免持锁等待把所有并发调用串成队。
         *
         * @param now         单调时钟当前值（纳秒）
         * @param budgetNanos 剩余可等待预算（纳秒）
         */
        synchronized Reservation reserve(long now, long budgetNanos) {
            refill(now);
            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return Reservation.granted();
            }
            long waitNanos = (long) Math.ceil((1.0d - tokens) / qps * TimeUnit.SECONDS.toNanos(1));
            if (waitNanos > budgetNanos) {
                return Reservation.exceeded();
            }
            return Reservation.waiting(waitNanos);
        }

        private void refill(long now) {
            long elapsed = now - lastNanos;
            if (elapsed <= 0) {
                // 时钟回拨（或测试换了时钟）时只对齐基准点，不补发令牌，避免凭空放大额度。
                return;
            }
            lastNanos = now;
            tokens = Math.min(qps, tokens + (double) elapsed / TimeUnit.SECONDS.toNanos(1) * qps);
        }
    }
}
