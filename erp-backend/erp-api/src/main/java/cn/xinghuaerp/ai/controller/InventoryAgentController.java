package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiInventoryReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/ai-agent/inventory")
public class InventoryAgentController extends BaseController {
    private final BusinessAccessService access;
    private final AiInventoryReadService inventory;
    private final PythonAgentClient python;
    private final AiAgentPermissionService permissions;
    public record InventoryRow(Long warehouseId, Long merchantId, Long shopId, Integer quantity,
                               Integer availableQuantity, Integer lockedQuantity, Integer stockStatus,
                               Long batchCount, Long batchQuantity, Long invalidBatches) {}
    public record IncomingBatch(Long quantity, Integer arrivalDays) {}
    public record Assumptions(BigDecimal dailyDemand, int targetDays, long confirmedIncoming, Integer incomingArrivalDays, Integer leadTimeDays, List<IncomingBatch> incomingBatches) {}
    public record Facts(Long skuId, Long warehouseId, int days, LocalDateTime retrievedAt,
                        List<InventoryRow> inventory, AiInventoryReadService.Sales sales,
                        AiInventoryReadService.PurchaseInbound purchase, String inventoryLimitation, Assumptions assumptions,
                        String question, boolean useModel) {}
    public record Request(Long skuId, Long warehouseId, int days, BigDecimal dailyDemand, Integer targetDays,
                          Long confirmedIncoming, String question, Integer incomingArrivalDays, Integer leadTimeDays, List<IncomingBatch> incomingBatches) {
        public Request(Long skuId, Long warehouseId, int days, BigDecimal dailyDemand, Integer targetDays, Long confirmedIncoming, String question) {
            this(skuId, warehouseId, days, dailyDemand, targetDays, confirmedIncoming, question, null, null, null);
        }
    }
    @PostMapping("/analysis")
    public AjaxResult analyze(@RequestBody Request request) {
        return success(analyzeFacts(request, true));
    }
    JsonNode analyzeFacts(Request request, boolean useModel) {
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        Long skuId = request.skuId(), warehouseId = request.warehouseId(), confirmedIncoming = request.confirmedIncoming();
        int days = request.days(); BigDecimal dailyDemand = request.dailyDemand(); Integer targetDays = request.targetDays();
        String question = !useModel || request.question() == null ? "" : request.question().trim();
        if (question.length() > 500) throw new IllegalArgumentException("问题不能超过500字");
        if (days != 7 && days != 30) throw new IllegalArgumentException("仅支持近7日或近30日");
        boolean anyAssumption = dailyDemand != null || targetDays != null || confirmedIncoming != null
                || request.incomingArrivalDays() != null || request.leadTimeDays() != null || request.incomingBatches() != null;
        if ((request.incomingArrivalDays() != null && (request.incomingArrivalDays() < 0 || request.incomingArrivalDays() > 3650))
                || (request.leadTimeDays() != null && (request.leadTimeDays() < 0 || request.leadTimeDays() > 3650)))
            throw new IllegalArgumentException("到货间隔和采购交期须为0至3650天");
        if (anyAssumption && (warehouseId == null || dailyDemand == null || dailyDemand.compareTo(new BigDecimal("0.000001")) < 0 || dailyDemand.scale() > 6
                || dailyDemand.compareTo(new BigDecimal("1000000")) > 0 || targetDays == null || targetDays < 1 || targetDays > 365
                || confirmedIncoming == null || confirmedIncoming < 0 || confirmedIncoming > 1000000000L))
            throw new IllegalArgumentException("请填写有效仓库及全部补货估算参数");
        if (request.incomingBatches() != null) {
            if (request.incomingBatches().size() > 50 || request.incomingArrivalDays() != null)
                throw new IllegalArgumentException("最多50批，不能同时填写单批到货间隔");
            long total = 0;
            for (var batch : request.incomingBatches()) {
                if (batch == null || batch.quantity() == null || batch.quantity() <= 0 || batch.quantity() > 1000000000L
                        || batch.arrivalDays() == null || batch.arrivalDays() < 0 || batch.arrivalDays() > 3650)
                    throw new IllegalArgumentException("批次数量或到货间隔无效");
                total += batch.quantity();
            }
            if (confirmedIncoming == null || total != confirmedIncoming) throw new IllegalArgumentException("批次数量之和必须等于确认在途总量");
        }
        Long userId = getUserId();
        if (!permissions.allows(userId, "ai:inventory:query")) throw new AccessDeniedException("无 AI 库存查询权限");
        var scope = access.scope(userId);
        boolean canSales = !"WAREHOUSE".equals(scope.type()) && permissions.allows(userId, "ai:sales:query");
        boolean canPurchase = !"WAREHOUSE".equals(scope.type()) && permissions.allows(userId, "ai:purchase:query");
        var snapshot = inventory.analysisSnapshot(skuId, warehouseId, days, scope, canSales, canPurchase);
        var audits = snapshot.batchAudit().stream().collect(java.util.stream.Collectors.toMap(a -> a.getInventoryId(), a -> a));
        var rows = snapshot.inventory().records().stream().map(r -> {
            var audit = audits.get(r.getId());
            return new InventoryRow(r.getWarehouseId(), r.getMerchantId(), r.getShopId(), r.getQuantity(), r.getAvailableQuantity(),
                    r.getLockedQuantity(), r.getStockStatus(), audit == null ? null : audit.getBatchCount(),
                    audit == null ? null : audit.getBatchQuantity(), audit == null ? null : audit.getInvalidBatches());
        }).toList();
        return python.analyze(new Facts(skuId, warehouseId, days, snapshot.retrievedAt(), rows, snapshot.sales(), snapshot.purchase(), snapshot.inventory().limitation(),
                anyAssumption ? new Assumptions(dailyDemand, targetDays, confirmedIncoming, request.incomingArrivalDays(), request.leadTimeDays(), request.incomingBatches()) : null, question, useModel));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException e) { return error(e.getMessage()); }
    @ExceptionHandler(IllegalStateException.class)
    public AjaxResult unavailable(IllegalStateException e) { return error(e.getMessage()); }
}
