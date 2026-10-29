package cn.xinghuaerp.open.common;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BoundedPlatformHttpTest {
 @Test void streamingOversizeCancelsSubscriptionWithoutWaitingForEnd() {
  var body=new BoundedPlatformHttp.LimitedBody();var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
  body.onSubscribe(new java.util.concurrent.Flow.Subscription(){public void request(long count){}public void cancel(){cancelled.set(true);}});
  body.onNext(List.of(ByteBuffer.wrap(new byte[8*1024*1024])));
  body.onNext(List.of(ByteBuffer.wrap(new byte[1])));
  assertTrue(cancelled.get());assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
 }
 @Test void utf8ResponseCompletesNormally() {
  var body=new BoundedPlatformHttp.LimitedBody();body.onSubscribe(new java.util.concurrent.Flow.Subscription(){public void request(long count){}public void cancel(){}});
  body.onNext(List.of(ByteBuffer.wrap("库存".getBytes(java.nio.charset.StandardCharsets.UTF_8))));body.onComplete();
  assertEquals("库存",body.getBody().toCompletableFuture().join());
 }
}
