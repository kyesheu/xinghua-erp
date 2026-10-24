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
        executor.submit(task);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
