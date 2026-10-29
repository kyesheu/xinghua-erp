package cn.xinghuaerp.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiTaskCancellationTest {
    @Test void disconnectInterruptsTheStreamingTask() throws Exception {
        var emitter=mock(SseEmitter.class);var completion=new AtomicReference<Runnable>();
        doAnswer(call->{completion.set(call.getArgument(0));return null;}).when(emitter).onCompletion(any());
        var started=new CountDownLatch(1);var stopped=new CountDownLatch(1);var executor=new AiTaskExecutor();
        var future=executor.submit(emitter,()->{started.countDown();try{Thread.sleep(60000);}catch(InterruptedException expected){stopped.countDown();Thread.currentThread().interrupt();}});
        try {
            assertTrue(started.await(2,TimeUnit.SECONDS));completion.get().run();
            assertTrue(stopped.await(2,TimeUnit.SECONDS));assertTrue(future.isCancelled());
        } finally {future.cancel(true);executor.shutdown();}
    }
}
