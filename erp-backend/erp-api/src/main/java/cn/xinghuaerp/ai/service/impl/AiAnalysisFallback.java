package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.order.service.OOrderService;
import cn.xinghuaerp.order.vo.SalesDailyVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 分析兜底：AI 服务不可用时按固定口径汇总业务数据，得出确定、可核对的摘要。
 *
 * <p>兜底不生成「结论」——没有模型参与时，任何运营建议都可能脱离数据。
 * 这里只摆事实：今日概况、趋势、低库存与待发货，让使用者仍能判断下一步动作。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalysisFallback {

    private final OOrderService orderService;
    private final InventoryTools inventoryTools;
    private final RefundTools refundTools;

    private final cn.xinghuaerp.system.service.BusinessAccessService access;

    private final cn.xinghuaerp.system.service.AiAgentPermissionService permissions;

    public String build(String analysisType,Long userId) {
        boolean salesAllowed, inventoryAllowed, riskAllowed;
        try {
            access.requireHeadquarters(userId);
            salesAllowed=permissions.allows(userId,"ai:sales:query");
            inventoryAllowed=permissions.allows(userId,"ai:inventory:query");
            riskAllowed=permissions.allows(userId,"ai:risk:query");
        }
        catch(RuntimeException denied) {
            return "AI 分析服务暂时不可用。当前账号未生成跨主体本地摘要，请到有权限的业务页面查询；未查询的数据不代表零。";
        }
        StringBuilder md = new StringBuilder();
        md.append("## 本地数据摘要\n\n");
        md.append("> AI 分析服务当前不可用，以下为系统按固定口径直接汇总的数据，不包含模型生成的结论与建议。")
                .append("数据日期：").append(LocalDate.now()).append("\n\n");

        md.append("### 今日概况\n\n");
        md.append("| 指标 | 数值 |\n| --- | --- |\n");
        SalesDailyVo today = salesAllowed ? safe(() -> orderService.getTodaySalesDaily(null)) : null;
        md.append("| 今日销售额 | ").append(today != null && today.getAmount() != null ? today.getAmount() : "不可用").append(" 元 |\n");
        md.append("| 今日订单数 | ").append(today != null && today.getCount() != null ? today.getCount() : "不可用").append(" 单 |\n");
        Integer waitShip = salesAllowed ? safe(() -> orderService.getWaitShipOrderAllCount(null)) : null;
        md.append("| 待发货订单 | ").append(waitShip == null ? "不可用" : waitShip).append(" 单 |\n\n");

        switch (analysisType == null ? "" : analysisType) {
            case "inventory" -> { if(inventoryAllowed) appendInventory(md); else md.append("当前无库存资料权限。\n"); }
            case "customer" -> { if(riskAllowed) appendRefund(md); else md.append("当前无售后资料权限。\n"); }
            case "operation" -> appendOperation(md, waitShip);
            case "purchase" -> appendPurchase(md);
            default -> { if(salesAllowed) appendSales(md); else md.append("当前无销售资料权限。\n"); }
        }

        md.append("\n---\n\n启动 AI 服务后重新发起分析，可获得趋势解读、异常识别与运营建议。\n");
        return md.toString();
    }

    private void appendSales(StringBuilder md) {
        md.append("### 近 7 日销售\n\n");
        List<SalesDailyVo> days = safe(orderService::salesDaily);
        if (days == null) {md.append("销售资料暂时不可用，不代表没有销售。\n");return;}
        if (days.isEmpty()) {
            md.append("暂无销售记录。\n");
            return;
        }
        md.append("| 日期 | 销售额(元) | 订单数 |\n| --- | --- | --- |\n");
        int limit = Math.min(days.size(), 7);
        for (int i = 0; i < limit; i++) {
            SalesDailyVo day = days.get(i);
            md.append("| ").append(day.getDate() == null ? "-" : day.getDate())
                    .append(" | ").append(day.getAmount() == null ? "不可用" : day.getAmount())
                    .append(" | ").append(day.getCount() == null ? "不可用" : day.getCount()).append(" |\n");
        }
    }

    private void appendInventory(StringBuilder md) {
        md.append("### 可用库存最少的 SKU\n\n");
        try {
            md.append(inventoryTools.getInventoryList(5, 20)).append("\n");
        } catch (RuntimeException e) {
            log.debug("兜底库存查询失败: {}", e.getMessage());
            md.append("库存数据查询失败。\n");
        }
    }

    private void appendRefund(StringBuilder md) {
        md.append("### 近 90 天退款记录\n\n");
        try {
            md.append(refundTools.getRefundList(90, null, null, 20)).append("\n");
        } catch (RuntimeException e) {
            log.debug("兜底退款查询失败: {}", e.getMessage());
            md.append("退款数据查询失败。\n");
        }
    }

    private void appendOperation(StringBuilder md, Integer waitShip) {
        md.append("### 发货积压\n\n");
        if(waitShip==null){md.append("待发货资料暂时不可用，不代表没有待发货订单。\n");return;}
        int count = waitShip;
        md.append(count > 0
                ? "当前有 " + count + " 单待发货，建议优先处理超时订单。\n"
                : "当前没有待发货订单。\n");
    }

    private void appendPurchase(StringBuilder md) {
        md.append("### 采购提示\n\n");
        md.append("采购分析需要结合销售速度与在途数量，AI 服务不可用时不做估算，请到采购与库存页面核对。\n");
    }

    /** 单项查询失败不影响整份摘要：兜底本身必须比被兜底的对象更稳。 */
    private static <T> T safe(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
