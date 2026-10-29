package cn.xinghuaerp.ai.service;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class SingleFlightCacheTest {
    @Test void expiryAndDifferentKeysDoNotReturnStaleOrForeignResults() {
        var clock=new AtomicLong();var cache=new SingleFlightCache<String,String>(clock::get);
        assertEquals("A",cache.get("a",()->"A",v->10));
        assertEquals("A",cache.get("a",()->"WRONG",v->10));
        assertEquals("B",cache.get("b",()->"B",v->10));
        clock.set(20_000_000);assertEquals("new",cache.get("b",()->"new",v->10));
    }
    @Test void concurrentMissCallsLoaderOnce() throws Exception {
        var cache=new SingleFlightCache<String,String>();var calls=new AtomicInteger();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->cache.get("same",()->{calls.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}return "ok";},v->1000));
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            var second=pool.submit(()->cache.get("same",()->{calls.incrementAndGet();return "wrong";},v->1000));
            release.countDown();assertEquals("ok",first.get());assertEquals("ok",second.get());assertEquals(1,calls.get());
        }
    }
    @Test void failedLoaderIsNotCached() {
        var cache=new SingleFlightCache<String,String>();
        assertThrows(IllegalStateException.class,()->cache.get("a",()->{throw new IllegalStateException();},v->100));
        assertEquals("recovered",cache.get("a",()->"recovered",v->100));
    }
}
