package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.ai.service.InventoryReviewWorkflowSync;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiInventoryReviewService;
import cn.xinghuaerp.ai.service.AiPurchaseDraftService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/ai-agent/inventory/review-tasks")
public class InventoryReviewController extends BaseController {
    private final PurchaseAccessService purchaseAccess;
    private final AiAgentPermissionService permissions;
    private final InventoryAgentController inventory;
    private final AiInventoryReviewService tasks;
    private final AiPurchaseDraftService drafts;
    private final InventoryReviewWorkflowSync workflow;
    public record Decision(int version, Boolean approved, String note, Integer approvedQuantity) {
        public Decision(int version, Boolean approved, String note) { this(version,approved,note,null); }
    }
    public record DraftRequest(int version, Long supplierId, BigDecimal unitPrice, Boolean confirmed) {}

    private void requireAccess(String purchasePermission) {
        Long userId = getUserId();
        purchaseAccess.requireHeadquarters(userId, purchasePermission);
        if (!permissions.allows(userId,"ai:inventory:query") || !permissions.allows(userId,"ai:sales:query") || !permissions.allows(userId,"ai:purchase:query"))
            throw new AccessDeniedException("审核资料需要库存、销售及采购 AI 查询权限");
    }
    private JsonNode comparableFacts(JsonNode result) {
        var facts = (ObjectNode) JsonUtils.readTree(JsonUtils.write(result.get("facts")));
        facts.remove("retrievedAt");
        return facts;
    }
    @PostMapping
    public AjaxResult create(@RequestHeader("X-Request-Key") String requestKey, @RequestBody InventoryAgentController.Request input) {
        requireAccess("purchase:order:create");
        purchaseAccess.requireHeadquarters(getUserId(),"purchase:order:query");
        try { java.util.UUID.fromString(requestKey); } catch (IllegalArgumentException error) { throw new IllegalArgumentException("请求标识无效"); }
        if (input == null || input.warehouseId() == null || input.dailyDemand() == null || input.targetDays() == null || input.confirmedIncoming() == null)
            throw new IllegalArgumentException("审核任务需要指定仓库和完整补货估算条件");
        var request = new InventoryAgentController.Request(input.skuId(),input.warehouseId(),input.days(),input.dailyDemand(),input.targetDays(),input.confirmedIncoming(),null,input.incomingArrivalDays(),input.leadTimeDays(),input.incomingBatches());
        String requestJson = JsonUtils.write(request);
        var existing = tasks.findByKey(getUserId(),requestKey);
        if (existing != null) {
            if (!existing.requestJson().equals(requestJson)) throw new IllegalStateException("请求标识已用于其他审核内容");
            return success(java.util.Map.of("id",existing.id()));
        }
        JsonNode result = inventory.analyzeFacts(request, false);
        Integer quantity = JsonUtils.integer(result,"suggestedQuantity");
        if (quantity == null || quantity <= 0) throw new IllegalArgumentException("当前数据不能形成正数补货建议，请核对资料");
        String id = tasks.create(getUserId(),requestKey,request.skuId(),request.warehouseId(),requestJson,JsonUtils.write(comparableFacts(result)),JsonUtils.write(result),quantity);
        return success(java.util.Map.of("id",id));
    }
    @GetMapping
    public AjaxResult recent() {
        requireAccess("purchase:order:query");
        return success(tasks.recent());
    }
    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable String id) {
        requireAccess("purchase:order:query");
        return success(tasks.get(id));
    }
    @PostMapping("/{id}/workflow-sync")
    public AjaxResult syncWorkflow(@PathVariable String id) {
        requireAccess("purchase:order:query");
        return success(workflow.sync(tasks.get(id)));
    }
    @PostMapping("/{id}/decision")
    public AjaxResult decide(@PathVariable String id, @RequestBody Decision decision) {
        requireAccess("purchase:order:audit");
        purchaseAccess.requireHeadquarters(getUserId(),"purchase:order:query");
        if (decision == null || decision.approved() == null || decision.version() < 1) throw new IllegalArgumentException("审核决定无效");
        String note = decision.note() == null ? "" : decision.note().trim();
        if (note.length() > 500) throw new IllegalArgumentException("审核意见不能超过500字");
        var task = tasks.get(id);
        if (!"PENDING".equals(task.status()) || task.version() != decision.version()) throw new IllegalStateException("审核任务已变化，请刷新后重试");
        Integer quantity = decision.approved() ? (decision.approvedQuantity() == null ? task.suggestedQuantity() : decision.approvedQuantity()) : null;
        if (decision.approved() && quantity <= 0) throw new IllegalArgumentException("审核数量必须为有效正整数");
        if (decision.approved() && quantity != task.suggestedQuantity() && note.isBlank()) throw new IllegalArgumentException("修改数量时请填写审核意见");
        if (!decision.approved() && decision.approvedQuantity() != null) throw new IllegalArgumentException("驳回时不能填写审核数量");
        if (decision.approved()) {
            var request = JsonUtils.readValue(task.requestJson(),InventoryAgentController.Request.class);
            var current = inventory.analyzeFacts(request, false);
            if (!comparableFacts(current).equals(JsonUtils.readTree(task.factsJson())) || !java.util.Objects.equals(JsonUtils.integer(current,"suggestedQuantity"),task.suggestedQuantity()))
                throw new IllegalStateException("库存、销量、采购资料或计算结果已变化，请重新分析并提交审核");
        }
        tasks.decide(id,decision.version(),decision.approved(),getUserId(),note,quantity);
        return success(tasks.get(id));
    }
    @PostMapping("/{id}/purchase-draft")
    public AjaxResult createDraft(@PathVariable String id, @RequestBody DraftRequest request) {
        requireAccess("purchase:order:create");
        purchaseAccess.requireHeadquarters(getUserId(),"purchase:order:query");
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) throw new IllegalArgumentException("请确认采购草稿内容");
        var task = tasks.get(id);
        if (!"APPROVED".equals(task.status()) || task.version() != request.version()) throw new IllegalStateException("审核任务未通过或版本已变化");
        var existing = drafts.findReviewedDraft(id,getUserId(),request.supplierId(),request.unitPrice());
        if (existing != null) return success(new InventoryPurchaseDraftController.Response(String.valueOf(existing.orderId()),true));
        var input = JsonUtils.readValue(task.requestJson(),InventoryAgentController.Request.class);
        var current = inventory.analyzeFacts(input,false);
        if (!comparableFacts(current).equals(JsonUtils.readTree(task.factsJson())) || !java.util.Objects.equals(JsonUtils.integer(current,"suggestedQuantity"),task.suggestedQuantity()))
            throw new IllegalStateException("库存、销量、采购资料或计算结果已变化，请重新分析并提交审核");
        var result = drafts.createFromReview(id,request.version(),request.supplierId(),request.unitPrice(),getUserId(),getUsername());
        return success(new InventoryPurchaseDraftController.Response(String.valueOf(result.orderId()),result.replayed()));
    }
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class})
    public AjaxResult invalid(RuntimeException error) { return error(error.getMessage()); }
}
