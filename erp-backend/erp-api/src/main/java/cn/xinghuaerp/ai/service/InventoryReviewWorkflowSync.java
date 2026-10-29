package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.ai.service.AiInventoryReviewService;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import cn.xinghuaerp.ai.service.PythonAgentClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryReviewWorkflowSync {
    private final AiInventoryReviewService tasks;
    private final PythonAgentClient python;
    private String scanAfterId="";

    public Object sync(AiInventoryReviewService.Task task) {
        var input = new HashMap<String,Object>();
        input.put("taskId", task.id());
        input.put("suggestedQuantity", task.suggestedQuantity());
        input.put("status", task.status());
        input.put("version", task.version());
        input.put("reviewedBy", task.reviewedBy());
        var result = python.syncInventoryReview(input);
        tasks.markWorkflowSynced(task.id(),task.version());
        return result;
    }

    @Scheduled(fixedDelayString = "${ERP_AGENT_REVIEW_SYNC_DELAY_MS:30000}")
    public void reconcile() {
        if (Thread.currentThread().isInterrupted()) return;
        for (var task : tasks.pendingWorkflowSync(scanAfterId)) {
            if (Thread.currentThread().isInterrupted()) return;
            scanAfterId=task.id();
            try { sync(task); }
            catch (RuntimeException error) {
                for (Throwable cause=error;cause!=null;cause=cause.getCause()) {
                    if (cause instanceof InterruptedException || cause instanceof java.util.concurrent.CancellationException) {
                        Thread.currentThread().interrupt();return;
                    }
                }
                if(Thread.currentThread().isInterrupted())return;
                log.warn("审核工作流同步失败，任务 {} 将重试，错误类型={}", task.id(), error.getClass().getSimpleName());
            }
        }
    }
}
