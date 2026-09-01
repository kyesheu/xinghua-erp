package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiFinanceReadService;
import cn.xinghuaerp.ai.service.AiSalesReadService;
import cn.xinghuaerp.ai.service.AiRiskSummaryService;
import cn.xinghuaerp.ai.service.AiInventoryReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/ai-agent/management")
public class ManagementAgentController extends BaseController {
    private final AiAgentPermissionService permissions;
    private final BusinessAccessService access;
    private final AiSalesReadService sales;
    private final AiFinanceReadService finance;
    private final AiRiskSummaryService risk;
    private final AiInventoryReadService inventory;
    private final PythonAgentClient python;

    record SalesFacts(long orders, long paidOrders, BigDecimal payment, long previousOrders,
                      long previousPaidOrders, BigDecimal previousPayment, SkuFacts sku, String limitation) {}
    /** SKU 表现摘要（裁剪自排行完整版）：仅集中度、象限分布与限制声明，不含逐行明细，控制载荷大小。 */
    record SkuFacts(long skuCount, Integer top5Share, Integer top10Share, Integer top20Share,
                    java.util.Map<String,Integer> quadrants, String limitation) {}
    record FinanceFacts(BigDecimal payment, BigDecimal refund, BigDecimal difference, boolean ledgerAvailable,
                        BigDecimal settlementProfit, long settledOrders, LossFacts loss, String limitation) {}
    /** 负利润订单摘要（裁剪自亏损穿透）：仅笔数、诱因分布与限制声明，不含逐单明细。 */
    record LossFacts(long count, java.util.Map<String,Long> causes, String limitation) {}
    record RiskFacts(long refunds, String limitation) {}
    record InventoryFacts(long total, long scanned, java.util.Map<String,Long> risks, String limitation) {}
    record Facts(int days, String scope, SalesFacts sales, FinanceFacts finance, RiskFacts risk, InventoryFacts inventory, String question, boolean useModel) {}
    public record Request(String question, boolean useModel) {}
    record Plan(String status, List<String> topics, Integer days) {}

    @PostMapping("/analysis")
    public AjaxResult analyze(@RequestParam(defaultValue="30") int days, @RequestParam(defaultValue="both") String topic,
                              @RequestBody(required=false) Request request) {
        if (days != 7 && days != 30) throw new IllegalArgumentException("仅支持7或30个完整自然日");
        if (!List.of("sales", "finance", "risk", "inventory", "both", "auto").contains(topic)) throw new IllegalArgumentException("不支持该汇总主题");
        String question = request == null || request.question() == null ? "" : request.question().trim();
        if (question.length() > 500) throw new IllegalArgumentException("问题不能超过500字");
        Long userId = getUserId();
        List<String> topics;
        if (topic.equals("auto")) {
            if (question.isBlank()) throw new IllegalArgumentException("请输入需要分析的问题");
            if (!permissions.allows(userId,"ai:sales:query") && !permissions.allows(userId,"ai:finance:query") && !permissions.allows(userId,"ai:risk:query") && !permissions.allows(userId,"ai:inventory:query"))
                throw new AccessDeniedException("无经营资料查询权限");
            var plan = JsonUtils.convert(python.managementPlan(java.util.Map.of("question", question)), Plan.class);
            if (plan == null || plan.status() == null) throw new IllegalStateException("任务分配结果无效，请重试");
            if (!"ok".equals(plan.status())) throw new IllegalStateException(switch (plan.status()) {
                case "disabled" -> "模型未启用，请手动选择资料类型";
                case "unsupported" -> "问题超出当前支持范围，请调整问题或手动选择资料类型";
                default -> "无法识别问题，请重试或手动选择资料类型";
            });
            topics = plan.topics();
            if (topics == null || topics.isEmpty() || !List.of("sales", "finance", "risk", "inventory").containsAll(topics))
                throw new IllegalStateException("任务分配结果无效，请重试");
            if (plan.days() != null) {
                if (plan.days() != 7 && plan.days() != 30) throw new IllegalStateException("任务分配的时间范围无效，请重试");
                days = plan.days();
            }
        } else topics = topic.equals("both") ? List.of("sales", "finance", "risk", "inventory") : List.of(topic);
        boolean canSales = topics.contains("sales") && permissions.allows(userId, "ai:sales:query");
        boolean canFinance = topics.contains("finance") && permissions.allows(userId, "ai:finance:query");
        boolean canRisk = topics.contains("risk") && permissions.allows(userId, "ai:risk:query");
        boolean canInventory = topics.contains("inventory") && permissions.allows(userId, "ai:inventory:query");
        if (topic.equals("auto") && ((topics.contains("sales") && !canSales) || (topics.contains("finance") && !canFinance) || (topics.contains("risk") && !canRisk) || (topics.contains("inventory") && !canInventory)))
            throw new AccessDeniedException("无权查询问题涉及的全部资料，请调整问题或联系管理员");
        if (!canSales && !canFinance && !canRisk && !canInventory) throw new AccessDeniedException("无经营资料查询权限");
        var scope = access.scope(userId);
        SalesFacts salesFacts = null;
        FinanceFacts financeFacts = null;
        RiskFacts riskFacts = null;
        InventoryFacts inventoryFacts = null;
        String label = "";
        if (canSales) {
            var data = sales.overview(days, scope);
            label = data.scope();
            // SKU 表现摘要：排行完整版裁剪填充，只传集中度/象限分布，不传逐行明细。
            var skuData = sales.skuPerformance(days, scope, 100);
            var shares = skuData.topShares();
            salesFacts = new SalesFacts(data.current().orders(), data.current().paidOrders(), data.current().payment(),
                    data.previous().orders(), data.previous().paidOrders(), data.previous().payment(),
                    new SkuFacts(skuData.skuCount(), shares.get("top5"), shares.get("top10"), shares.get("top20"),
                            skuData.quadrantCounts(), skuData.limitation()),
                    data.limitation());
        }
        if (canFinance) {
            var data = finance.overview(days, scope);
            label = data.scope();
            // 负利润订单摘要：亏损穿透裁剪填充，只传笔数与诱因分布；结算表缺失时不下发，避免误读为零亏损。
            var lossData = finance.lossOrders(days, scope);
            var loss = lossData.available() ? new LossFacts(lossData.count(), lossData.causeCounts(), lossData.limitation()) : null;
            financeFacts = new FinanceFacts(data.orders().payment(), data.refunds().amount(), data.paymentLessRefund(), data.ledger().available(),
                    data.settlement().available() && data.settlement().settledOrders() > 0 ? data.profit() : null,
                    data.settlement().settledOrders(), loss, data.limitation());
        }
        if (canRisk) {
            var data = risk.overview(days, scope);
            label = data.scope();
            riskFacts = new RiskFacts(data.refunds(), data.limitation());
        }
        if (canInventory) {
            var data = inventory.riskSummary(scope);
            label = data.scope();
            inventoryFacts = new InventoryFacts(data.total(), data.total(), data.risks(), "按授权范围全部SKU的库存总览状态统计；实时库存不是周期销量。" + data.limitation());
        }
        return success(python.managementAnalysis(new Facts(days, label, salesFacts, financeFacts, riskFacts, inventoryFacts, question, request != null && request.useModel())));
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public AjaxResult invalid(RuntimeException error) { return error(error.getMessage()); }
}
