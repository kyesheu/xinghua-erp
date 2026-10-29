package cn.xinghuaerp.ai.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 流式调用的执行器。
 *
 * <p>对话与分析都是「服务端读外部流、边读边推前端」的阻塞 IO，占一个平台线程非常浪费，
 * 因此用虚拟线程承载：既能保持 SseEmitter 立即返回，也不必为并发数预设线程池大小。</p>
 */
@Component
public class AiTaskExecutor {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void submit(Runnable task) {
        executor.submit(withContext(task));
    }

    public java.util.concurrent.Future<?> submit(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter, Runnable task) {
        var stopped=new java.util.concurrent.atomic.AtomicBoolean();
        var future=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        Runnable cancel=()->{stopped.set(true);var value=future.get();if(value!=null)value.cancel(true);};
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(error->cancel.run());
        var value=executor.submit(withContext(()->{if(!stopped.get())task.run();}));
        future.set(value);if(stopped.get())value.cancel(true);
        return value;
    }

    private Runnable withContext(Runnable task) {
        var captured=org.slf4j.MDC.getCopyOfContextMap();
        return ()->{var previous=org.slf4j.MDC.getCopyOfContextMap();try {
            if(captured==null)org.slf4j.MDC.clear();else org.slf4j.MDC.setContextMap(captured);task.run();
        } finally {if(previous==null)org.slf4j.MDC.clear();else org.slf4j.MDC.setContextMap(previous);}};
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
