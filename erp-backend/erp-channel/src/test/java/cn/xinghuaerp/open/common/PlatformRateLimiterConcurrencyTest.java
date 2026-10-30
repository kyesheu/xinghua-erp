package cn.xinghuaerp.open.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlatformRateLimiter} 的<b>真实并发</b>测试（不用假时钟、不替换 sleeper）。
 *
 * <p>为什么必须另写一个「真时间」的测试：{@code PlatformRateLimiterTest} 里的用例全部把
 * sleeper 换成「推进假时钟」，单线程下只证明了「算出来的等待时长是对的」，
 * 完全没有触及 {@code synchronized reserve(...)} 在多线程竞争时的行为——
 * 而这正是「限流器写错就超发」唯一可能出现的地方。
 *
 * <p><b>这两个断言能抓什么、抓不到什么</b>（务必读，不要over-claim）：
 * <ul>
 *   <li>耗时下限 {@code elapsed >= 70% × (N - QPS)/QPS}：抓的是「令牌总量被放大」——
 *       例如 reserve 在等待期间不预扣却每次醒来都扣掉一个不存在的令牌、桶容量被错误地按线程克隆、
 *       或 refill 用了墙钟又被多个线程重复补。这些错误都会让 N 次调用比令牌守恒的物理下限更快完成。
 *       抓不到：偶尔一两个线程多拿到一个令牌（误差在 30% 内看不见）。</li>
 *   <li>任意 1 秒窗口内授予数 {@code <= QPS + 桶容量(QPS)}：抓的是「瞬时超发」——
 *       令牌桶允许 1 秒突发的物理上限就是「一桶 + 一秒补充」= 2×QPS；超过它说明同一时刻
 *       有多个线程抢到了同一个令牌。抓不到：窗口边界上的抖动（所以留了 +2 的容差），
 *       也抓不到小于时间分辨率（记录时刻即是 acquire 返回时刻）的微小超发。</li>
 *   <li>抓不到：跨进程/多实例的总速率（本类是进程内限流，多实例叠加是设计已声明的边界）。</li>
 * </ul>
 */
class PlatformRateLimiterConcurrencyTest {

    private static final String TAO_URL = "https://api.taobao.com/router/rest";
    private static final String PDD_URL = "https://gw-api.pinduoduo.com/api/router";

    @BeforeEach
    void resetBefore() {
        PlatformRateLimiter.resetForTest();
    }

    @AfterEach
    void resetAfter() {
        PlatformRateLimiter.resetForTest();
    }

    @Test
    void concurrentAcquireStaysWithinConfiguredRate() throws Exception {
        final int qps = 50;
        final int threads = 8;
        final int perThread = 20;
        final int total = threads * perThread; // 160

        PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.TAO, qps));
        // 预算放到 10 秒：足够覆盖 160 次调用的排队，避免把「预算不足快速失败」混进本测试
        PlatformRateLimiter.setWaitBudgetMillis(10_000);

        List<Long> grantedAt = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failures = new AtomicInteger();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                try {
                    ready.countDown();
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        PlatformRateLimiter.acquire(TAO_URL);
                        grantedAt.add(System.nanoTime());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    firstError.compareAndSet(null, e);
                    failures.incrementAndGet();
                } catch (Throwable e) {
                    firstError.compareAndSet(null, e);
                    failures.incrementAndGet();
                }
            }, "rate-limit-worker");
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }

        ready.await(10, TimeUnit.SECONDS);
        long beganAt = System.nanoTime();
        start.countDown();
        for (Thread worker : workers) {
            worker.join(TimeUnit.SECONDS.toMillis(60));
            assertFalse(worker.isAlive(), "160 次调用应在 60 秒内全部完成，线程仍在等待说明限流器卡死");
        }
        long elapsedNanos = System.nanoTime() - beganAt;

        assertEquals(0, failures.get(), "并发取令牌不应抛异常（等待预算 10 秒足够）：" + firstError.get());
        assertEquals(total, grantedAt.size(), "所有调用都应最终拿到令牌");

        double elapsedSeconds = elapsedNanos / 1e9d;
        // 令牌守恒的物理下限：起步满桶（qps 个）之后，剩余 total-qps 个只能按 qps/秒 发放
        double floorSeconds = (total - qps) / (double) qps; // 2.2s
        int maxInOneSecond = maxGrantedInWindow(grantedAt, TimeUnit.SECONDS.toNanos(1));

        System.out.printf("[并发限流] total=%d qps=%d 耗时=%.3fs 物理下限=%.3fs 最密 1s 窗口授予=%d（上限 %d）%n",
                total, qps, elapsedSeconds, floorSeconds, maxInOneSecond, 2 * qps + 2);

        assertTrue(elapsedSeconds >= 0.70d * floorSeconds,
                String.format("160 次调用在 %d QPS 下不可能快于 %.1fs（令牌守恒），实际 %.3fs："
                        + "说明令牌被放大了（重复扣减/重复补充/桶被克隆）", qps, floorSeconds, elapsedSeconds));
        assertTrue(elapsedSeconds < 3 * floorSeconds + 2,
                String.format("实际耗时 %.3fs 远高于下限 %.1fs：多线程下被串行化或反复争抢，限流器把并发退化成排队",
                        elapsedSeconds, floorSeconds));
        assertTrue(maxInOneSecond <= 2 * qps + 2,
                String.format("任意 1 秒窗口最多授予「一桶 + 一秒补充」= %d 次，实际观测到 %d 次："
                        + "同一时刻有多个线程抢到了同一个令牌（超发）", 2 * qps, maxInOneSecond));
    }

    @Test
    void failingFastOnSmallBudgetDoesNotOverIssueNorHang() throws Exception {
        final int qps = 20;
        final int total = 40;

        PlatformRateLimiter.configure(Map.of(PlatformRateLimiter.PDD, qps));
        // 一次补充 50ms，预算 100ms：只够等一两轮，剩下的调用必须快速失败，而不是无限排队
        PlatformRateLimiter.setWaitBudgetMillis(100);

        List<Long> grantedAt = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);

        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < total / 4; i++) {
                        try {
                            PlatformRateLimiter.acquire(PDD_URL);
                            grantedAt.add(System.nanoTime());
                        } catch (java.io.IOException e) {
                            if (e.getMessage() != null && e.getMessage().contains("平台调用频率超限")) {
                                rejected.incrementAndGet();
                            } else {
                                unexpected.compareAndSet(null, e);
                                otherFailures.incrementAndGet();
                            }
                        }
                    }
                } catch (Throwable e) {
                    unexpected.compareAndSet(null, e);
                    otherFailures.incrementAndGet();
                }
            }, "fail-fast-worker");
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }

        long beganAt = System.nanoTime();
        start.countDown();
        for (Thread worker : workers) {
            worker.join(TimeUnit.SECONDS.toMillis(30));
            assertFalse(worker.isAlive(), "等待预算耗尽后必须快速失败，不允许线程无限等待");
        }
        long elapsedMillis = (System.nanoTime() - beganAt) / 1_000_000L;

        assertEquals(0, otherFailures.get(), "只允许「平台调用频率超限」这一种异常：" + unexpected.get());
        assertEquals(total, grantedAt.size() + rejected.get(), "每次调用要么拿到令牌要么被拒绝，不能凭空消失");
        assertTrue(rejected.get() > 0, "预算 100ms、qps 20 时应当有调用被快速拒绝，实际一个都没有");

        int maxInOneSecond = maxGrantedInWindow(grantedAt, TimeUnit.SECONDS.toNanos(1));
        System.out.printf("[快速失败] 放行=%d 拒绝=%d 耗时=%dms 最密 1s 窗口授予=%d%n",
                grantedAt.size(), rejected.get(), elapsedMillis, maxInOneSecond);
        assertTrue(maxInOneSecond <= 2 * qps + 2,
                "被拒绝的那部分调用不能导致已放行的令牌超发，实际窗口内 " + maxInOneSecond + " 次");
    }

    /** 排序后求「任意 {@code windowNanos} 滑窗内的最大计数」。 */
    private static int maxGrantedInWindow(List<Long> grantedAt, long windowNanos) {
        long[] sorted;
        synchronized (grantedAt) {
            sorted = grantedAt.stream().mapToLong(Long::longValue).sorted().toArray();
        }
        int max = 0;
        int tail = 0;
        for (int head = 0; head < sorted.length; head++) {
            while (sorted[head] - sorted[tail] >= windowNanos) {
                tail++;
            }
            max = Math.max(max, head - tail + 1);
        }
        return max;
    }
}
