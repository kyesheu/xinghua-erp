package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.ai.service.AiInventoryReviewService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.ai.service.InventoryReviewWorkflowSync;

class InventoryReviewWorkflowSyncTest {
    @Test void failedSyncIsRetriedWithoutMarkingAndSuccessfulSyncMarksVersion() {
        var tasks = mock(AiInventoryReviewService.class);
        var python = mock(PythonAgentClient.class);
        var task = new AiInventoryReviewService.Task("task",7,5,1,"{}","{}","{}",12,"PENDING",1,LocalDateTime.now(),null,null,null,null);
        when(tasks.pendingWorkflowSync(anyString())).thenReturn(List.of(task));
        when(python.syncInventoryReview(any())).thenThrow(new IllegalStateException("unavailable")).thenReturn(null);
        var sync = new InventoryReviewWorkflowSync(tasks,python);
        sync.reconcile();
        verify(tasks,never()).markWorkflowSynced(anyString(),anyInt());
        sync.reconcile();
        verify(tasks).markWorkflowSynced("task",1);
    }
    @Test void interruptedWorkerDoesNotFetchOrDispatchMoreTasks() {
        var tasks=mock(AiInventoryReviewService.class);var python=mock(PythonAgentClient.class);
        Thread.currentThread().interrupt();
        try {new InventoryReviewWorkflowSync(tasks,python).reconcile();verifyNoInteractions(tasks,python);}
        finally {Thread.interrupted();}
    }
    @Test void wrappedInterruptionStopsBatchWithoutMarkingTaskSynced() {
        var tasks=mock(AiInventoryReviewService.class);var python=mock(PythonAgentClient.class);
        var task=new AiInventoryReviewService.Task("task",7,5,1,"{}","{}","{}",12,"PENDING",1,LocalDateTime.now(),null,null,null,null);
        when(tasks.pendingWorkflowSync(anyString())).thenReturn(List.of(task,task));
        when(python.syncInventoryReview(any())).thenThrow(new IllegalStateException(new InterruptedException()));
        try {
            new InventoryReviewWorkflowSync(tasks,python).reconcile();
            org.junit.jupiter.api.Assertions.assertTrue(Thread.currentThread().isInterrupted());
            verify(python,times(1)).syncInventoryReview(any());verify(tasks,never()).markWorkflowSynced(anyString(),anyInt());
        } finally {Thread.interrupted();}
    }
}
