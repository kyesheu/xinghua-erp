package cn.xinghuaerp.ai.service;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.*;

/** 单条短缓存与并发合并：不同 key 不共享结果，等待有上限。 */
public class SingleFlightCache<K,V> {
    private K key, flightKey;
    private V value;
    private long expires;
    private CompletableFuture<V> flight;
    private final LongSupplier clock;
    public SingleFlightCache() {this(System::nanoTime);}
    SingleFlightCache(LongSupplier clock) {this.clock=clock;}
    public V get(K requested,Supplier<V> loader,ToLongFunction<V> ttlMillis) {
        CompletableFuture<V> waiting;boolean owner=false;
        synchronized(this) {
            if(value!=null && Objects.equals(key,requested) && clock.getAsLong()<expires)return value;
            if(flight!=null) {
                if(!Objects.equals(flightKey,requested))throw new IllegalStateException("简报配置正在切换，请稍后重试");
                waiting=flight;
            } else {waiting=new CompletableFuture<>();flight=waiting;flightKey=requested;owner=true;}
        }
        if(!owner)try {return waiting.get(1,TimeUnit.SECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("等待简报已取消",e);}
        catch(ExecutionException | TimeoutException e){throw new IllegalStateException("简报正在生成或暂时不可用，请稍后重试",e);}
        try {
            V loaded=loader.get();
            synchronized(this){key=requested;value=loaded;expires=clock.getAsLong()+TimeUnit.MILLISECONDS.toNanos(Math.max(0,ttlMillis.applyAsLong(loaded)));}
            waiting.complete(loaded);return loaded;
        } catch(RuntimeException error){waiting.completeExceptionally(error);throw error;}
        finally {synchronized(this){flight=null;flightKey=null;}}
    }
}
