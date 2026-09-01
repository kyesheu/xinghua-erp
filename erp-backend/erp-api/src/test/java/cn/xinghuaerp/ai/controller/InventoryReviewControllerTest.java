package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.ai.service.InventoryReviewWorkflowSync;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.ai.service.AiInventoryReviewService;
import cn.xinghuaerp.ai.service.AiPurchaseDraftService;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.controller.InventoryAgentController;
import cn.xinghuaerp.ai.controller.InventoryPurchaseDraftController;
import cn.xinghuaerp.ai.controller.InventoryReviewController;

class InventoryReviewControllerTest {
    final PurchaseAccessService purchase = mock(PurchaseAccessService.class);
    final AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
    final InventoryAgentController inventory = mock(InventoryAgentController.class);
    final AiInventoryReviewService tasks = mock(AiInventoryReviewService.class);
    final AiPurchaseDraftService drafts = mock(AiPurchaseDraftService.class);
    final InventoryReviewWorkflowSync workflow = mock(InventoryReviewWorkflowSync.class);
    final InventoryReviewController controller = new InventoryReviewController(purchase,permissions,inventory,tasks,drafts,workflow) {
        @Override public Long getUserId() { return 7L; }
        @Override public String getUsername() { return "tester"; }
    };
    final InventoryAgentController.Request request = new InventoryAgentController.Request(5L,1L,7,new BigDecimal("2"),10,0L,"private question");
    @BeforeEach void permissions() {
        when(permissions.allows(eq(7L),anyString())).thenReturn(true);
    }
    AiInventoryReviewService.Task pending() {
        var clean = new InventoryAgentController.Request(5L,1L,7,new BigDecimal("2"),10,0L,null);
        return new AiInventoryReviewService.Task("task",7,5,1,JsonUtils.write(clean),"{\"skuId\":5,\"inventory\":[{\"quantity\":8}],\"useModel\":false}","{}",12,"PENDING",1,LocalDateTime.now(),null,null,null,null);
    }
    @Test void createRecalculatesRuleOnlyAndOmitsQuestion() {
        when(inventory.analyzeFacts(any(),eq(false))).thenReturn(JsonUtils.readTree("{\"suggestedQuantity\":12,\"facts\":{\"skuId\":5,\"retrievedAt\":\"old\",\"inventory\":[{\"quantity\":8}],\"useModel\":false}}"));
        when(tasks.create(anyLong(),anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString(),anyInt())).thenReturn("task");
        controller.create("9cd00957-f254-4de5-8e90-d716cc3bbf6f",request);
        verify(purchase).requireHeadquarters(7L,"purchase:order:create");
        verify(inventory).analyzeFacts(argThat(r -> r.question() == null),eq(false));
        verify(tasks).create(eq(7L),anyString(),eq(5L),eq(1L),argThat(s -> !s.contains("private question")),argThat(s -> !s.contains("retrievedAt")),anyString(),eq(12));
    }
    @Test void missingPermissionDoesNotReadOrPersist() {
        when(permissions.allows(7L,"ai:purchase:query")).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->controller.create("9cd00957-f254-4de5-8e90-d716cc3bbf6f",request));
        verifyNoInteractions(inventory,tasks);
    }
    @Test void workflowSyncUsesPersistedTaskAndChecksPermission() {
        when(tasks.get("task")).thenReturn(pending());
        controller.syncWorkflow("task");
        verify(purchase).requireHeadquarters(7L,"purchase:order:query");
        verify(workflow).sync(argThat(task -> "task".equals(task.id()) && "PENDING".equals(task.status()) && task.suggestedQuantity()==12));
    }
    @Test void workflowSyncRejectsMissingPermission() {
        when(permissions.allows(7L,"ai:sales:query")).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> controller.syncWorkflow("task"));
        verifyNoInteractions(tasks,workflow);
    }
    @Test void retryReturnsExistingTaskWithoutReanalysis() {
        String key = "9cd00957-f254-4de5-8e90-d716cc3bbf6f";
        when(tasks.findByKey(7L,key)).thenReturn(pending());
        var result = controller.create(key,request);
        assertEquals("task",((java.util.Map<?,?>)result.get("data")).get("id"));
        verifyNoInteractions(inventory);
        verify(tasks,never()).create(anyLong(),anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString(),anyInt());
    }
    @Test void approvalRejectsChangedSnapshot() {
        when(tasks.get("task")).thenReturn(pending());
        when(inventory.analyzeFacts(any(),eq(false))).thenReturn(JsonUtils.readTree("{\"suggestedQuantity\":12,\"facts\":{\"skuId\":5,\"retrievedAt\":\"new\",\"inventory\":[{\"quantity\":9}],\"useModel\":false}}"));
        assertThrows(IllegalStateException.class,()->controller.decide("task",new InventoryReviewController.Decision(1,true,"核对")));
        verify(tasks,never()).decide(anyString(),anyInt(),anyBoolean(),anyLong(),anyString(),any());
    }
    @Test void approvalAcceptsSameFactsDespiteNewRetrievalTime() {
        when(tasks.get("task")).thenReturn(pending());
        when(inventory.analyzeFacts(any(),eq(false))).thenReturn(JsonUtils.readTree("{\"suggestedQuantity\":12,\"facts\":{\"skuId\":5,\"retrievedAt\":\"new\",\"inventory\":[{\"quantity\":8}],\"useModel\":false}}"));
        controller.decide("task",new InventoryReviewController.Decision(1,true,"核对"));
        verify(purchase).requireHeadquarters(7L,"purchase:order:audit");
        verify(tasks).decide("task",1,true,7L,"核对",12);
    }
    @Test void rejectionRecordsDecisionWithoutReanalysis() {
        when(tasks.get("task")).thenReturn(pending());
        controller.decide("task",new InventoryReviewController.Decision(1,false,"资料需补充"));
        verifyNoInteractions(inventory);
        verify(tasks).decide("task",1,false,7L,"资料需补充",null);
    }
    @Test void modifiedQuantityRequiresReasonAndPersistsWithDecision() {
        when(tasks.get("task")).thenReturn(pending());
        assertThrows(IllegalArgumentException.class, () -> controller.decide("task",new InventoryReviewController.Decision(1,true,"",20)));
        verify(tasks,never()).decide(anyString(),anyInt(),anyBoolean(),anyLong(),anyString(),any());
        when(inventory.analyzeFacts(any(),eq(false))).thenReturn(JsonUtils.readTree("{\"suggestedQuantity\":12,\"facts\":{\"skuId\":5,\"inventory\":[{\"quantity\":8}],\"useModel\":false}}"));
        controller.decide("task",new InventoryReviewController.Decision(1,true,"整箱采购",20));
        verify(tasks).decide("task",1,true,7L,"整箱采购",20);
    }
    @Test void revokedPurchaseAuditPermissionBlocksDecisionBeforeReadingTask() {
        doThrow(new AccessDeniedException("无采购操作权限"))
                .when(purchase).requireHeadquarters(7L,"purchase:order:audit");
        assertThrows(AccessDeniedException.class,()->controller.decide("task",new InventoryReviewController.Decision(1,true,"")));
        verifyNoInteractions(tasks,inventory);
    }
    @Test void reviewedDraftRejectsChangedFactsBeforeWriting() {
        var t = pending();
        when(tasks.get("task")).thenReturn(new AiInventoryReviewService.Task(t.id(),t.createdBy(),t.skuId(),t.warehouseId(),t.requestJson(),t.factsJson(),t.resultJson(),t.suggestedQuantity(),"APPROVED",2,t.createdAt(),7L,LocalDateTime.now(),"",12));
        when(inventory.analyzeFacts(any(),eq(false))).thenReturn(JsonUtils.readTree("{\"suggestedQuantity\":12,\"facts\":{\"skuId\":5,\"inventory\":[{\"quantity\":9}],\"useModel\":false}}"));
        assertThrows(IllegalStateException.class,()->controller.createDraft("task",new InventoryReviewController.DraftRequest(2,10L,new BigDecimal("9.50"),true)));
        verify(drafts,never()).createFromReview(anyString(),anyInt(),any(),any(),any(),any());
    }
    @Test void reviewedDraftRejectsChangedRuleResultEvenWithSameFacts() {
        var t = pending();
        when(tasks.get("task")).thenReturn(new AiInventoryReviewService.Task(t.id(),t.createdBy(),t.skuId(),t.warehouseId(),t.requestJson(),t.factsJson(),t.resultJson(),t.suggestedQuantity(),"APPROVED",2,t.createdAt(),7L,LocalDateTime.now(),"",12));
        when(inventory.analyzeFacts(any(),eq(false))).thenReturn(JsonUtils.readTree("{\"suggestedQuantity\":13,\"facts\":{\"skuId\":5,\"inventory\":[{\"quantity\":8}],\"useModel\":false}}"));
        assertThrows(IllegalStateException.class,()->controller.createDraft("task",new InventoryReviewController.DraftRequest(2,10L,new BigDecimal("9.50"),true)));
        verify(drafts,never()).createFromReview(anyString(),anyInt(),any(),any(),any(),any());
    }
    @Test void reviewedDraftRequiresCurrentCreatePermission() {
        doThrow(new AccessDeniedException("无采购操作权限"))
                .when(purchase).requireHeadquarters(7L,"purchase:order:create");
        assertThrows(AccessDeniedException.class,()->controller.createDraft("task",new InventoryReviewController.DraftRequest(2,10L,new BigDecimal("9.50"),true)));
        verifyNoInteractions(tasks,inventory,drafts);
    }
    @Test void reviewedDraftRequiresExplicitConfirmation() {
        assertThrows(IllegalArgumentException.class,()->controller.createDraft("task",new InventoryReviewController.DraftRequest(2,10L,new BigDecimal("9.50"),false)));
        verifyNoInteractions(tasks,inventory,drafts);
    }
    @Test void pendingReviewCannotCreatePurchaseDraft() {
        when(tasks.get("task")).thenReturn(pending());
        assertThrows(IllegalStateException.class,()->controller.createDraft("task",new InventoryReviewController.DraftRequest(1,10L,new BigDecimal("9.50"),true)));
        verifyNoInteractions(inventory,drafts);
    }
    @Test void staleApprovedVersionCannotCreatePurchaseDraft() {
        var t = pending();
        when(tasks.get("task")).thenReturn(new AiInventoryReviewService.Task(t.id(),t.createdBy(),t.skuId(),t.warehouseId(),t.requestJson(),t.factsJson(),t.resultJson(),t.suggestedQuantity(),"APPROVED",2,t.createdAt(),7L,LocalDateTime.now(),"",12));
        assertThrows(IllegalStateException.class,()->controller.createDraft("task",new InventoryReviewController.DraftRequest(1,10L,new BigDecimal("9.50"),true)));
        verifyNoInteractions(inventory,drafts);
    }
    @Test void reviewedDraftRetryReturnsExistingWithoutReanalysis() {
        var t = pending();
        when(tasks.get("task")).thenReturn(new AiInventoryReviewService.Task(t.id(),t.createdBy(),t.skuId(),t.warehouseId(),t.requestJson(),t.factsJson(),t.resultJson(),t.suggestedQuantity(),"APPROVED",2,t.createdAt(),7L,LocalDateTime.now(),"",12));
        when(drafts.findReviewedDraft("task",7L,10L,new BigDecimal("9.50"))).thenReturn(new AiPurchaseDraftService.Result(99L,true));
        var result = controller.createDraft("task",new InventoryReviewController.DraftRequest(2,10L,new BigDecimal("9.50"),true));
        assertEquals("99",((InventoryPurchaseDraftController.Response)result.get("data")).orderId());
        verifyNoInteractions(inventory);
        verify(drafts,never()).createFromReview(anyString(),anyInt(),any(),any(),any(),any());
    }
    @Test void approvedDraftRevalidatesAndCreatesUsingTaskVersion() {
        var t = pending();
        when(tasks.get("task")).thenReturn(new AiInventoryReviewService.Task(t.id(),t.createdBy(),t.skuId(),t.warehouseId(),t.requestJson(),t.factsJson(),t.resultJson(),t.suggestedQuantity(),"APPROVED",2,t.createdAt(),7L,LocalDateTime.now(),"",12));
        when(inventory.analyzeFacts(any(),eq(false))).thenReturn(JsonUtils.readTree("{\"suggestedQuantity\":12,\"facts\":{\"skuId\":5,\"retrievedAt\":\"new\",\"inventory\":[{\"quantity\":8}],\"useModel\":false}}"));
        when(drafts.createFromReview("task",2,10L,new BigDecimal("9.50"),7L,"tester")).thenReturn(new AiPurchaseDraftService.Result(99L,false));
        var result = controller.createDraft("task",new InventoryReviewController.DraftRequest(2,10L,new BigDecimal("9.50"),true));
        assertEquals("99",((InventoryPurchaseDraftController.Response)result.get("data")).orderId());
        verify(drafts).createFromReview("task",2,10L,new BigDecimal("9.50"),7L,"tester");
    }
}
