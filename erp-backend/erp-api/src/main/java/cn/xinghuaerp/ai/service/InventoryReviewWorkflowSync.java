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
        for (var task : tasks.pendingWorkflowSync()) {
            try { sync(task); }
            catch (RuntimeException error) { log.warn("审核工作流同步失败，任务 {} 将重试：{}", task.id(), error.getMessage()); }
        }
    }
}
