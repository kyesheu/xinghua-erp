package cn.xinghuaerp.open.common;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.io.*;
/** JDK渠道客户端：响应完成前持续执行大小及请求期限约束。 */
public final class BoundedPlatformHttp {
 private BoundedPlatformHttp() {}
 private static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 public static HttpResponse<String> send(HttpRequest original)throws IOException,InterruptedException {
  var request=HttpRequest.newBuilder(original,(name,value)->true).timeout(java.time.Duration.ofSeconds(30)).build();
  return CLIENT.send(request,info->new LimitedBody());
 }
 static final class LimitedBody implements HttpResponse.BodySubscriber<String> {
  private final CompletableFuture<String> future=new CompletableFuture<>();
  private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  private Flow.Subscription subscription;
  public CompletionStage<String> getBody(){return future;}
  public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
  public void onNext(List<ByteBuffer> chunks) {
   if(future.isDone())return;
   for(var chunk:chunks) {
    if(chunk.remaining()>8*1024*1024-bytes.size()){subscription.cancel();future.completeExceptionally(new IOException("平台响应超过8MiB"));return;}
    byte[] part=new byte[chunk.remaining()];chunk.get(part);bytes.writeBytes(part);
   }
   subscription.request(1);
  }
  public void onError(Throwable error){future.completeExceptionally(error);}
  public void onComplete(){future.complete(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));}
 }
}
