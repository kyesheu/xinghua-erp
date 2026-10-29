package cn.xinghuaerp.order.service.impl;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拉取重入守卫：同一店铺同类拉取只能有一个在跑，结束后必须能重新进入。
 * 守卫漏掉 end 会让店铺永久卡住，所以这里同时断言失败/异常路径也能释放。
 */
class PlatformPullGuardTest {

    @Test
    void secondPullOfSameShopAndTypeIsRejected() {
        var guard = new PlatformPullGuard();
        assertTrue(guard.tryBegin(1L, "ORDER"));
        assertFalse(guard.tryBegin(1L, "ORDER"), "同店同类拉取不能并发进入");
        assertEquals(1, guard.runningCount());

        guard.end(1L, "ORDER");
        assertTrue(guard.tryBegin(1L, "ORDER"), "上一次结束后必须能重新进入");
        guard.end(1L, "ORDER");
        assertEquals(0, guard.runningCount());
    }

    @Test
    void differentShopOrDifferentTypeDoNotBlockEachOther() {
        var guard = new PlatformPullGuard();
        assertTrue(guard.tryBegin(1L, "ORDER"));
        assertTrue(guard.tryBegin(2L, "ORDER"), "不同店铺互不影响");
        assertTrue(guard.tryBegin(1L, "REFUND"), "同店不同业务互不影响");
        guard.end(1L, "ORDER");
        guard.end(2L, "ORDER");
        guard.end(1L, "REFUND");
        assertEquals(0, guard.runningCount());
    }

    @Test
    void concurrentCallersOnlyOneWins() throws Exception {
        var guard = new PlatformPullGuard();
        int threads = 8;
        var start = new CountDownLatch(1);
        var won = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    if (guard.tryBegin(9L, "ORDER")) {
                        won.incrementAndGet();
                        // 持有一小会儿，让其余线程确实撞上
                        Thread.sleep(200);
                        guard.end(9L, "ORDER");
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "线程池未按时结束");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, won.get(), "并发下只能有一个拉取进入，其余必须被拒绝");
        assertEquals(0, guard.runningCount());
    }

    @Test
    void nullArgumentsDoNotThrowAndDoNotBlock() {
        var guard = new PlatformPullGuard();
        assertTrue(guard.tryBegin(null, "ORDER"));
        guard.end(null, "ORDER");
        assertEquals(0, guard.runningCount());
    }
}
