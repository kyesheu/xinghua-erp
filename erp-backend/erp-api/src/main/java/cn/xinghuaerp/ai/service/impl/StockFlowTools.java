package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.inventory.entity.ErpStockIn;
import cn.xinghuaerp.inventory.entity.ErpStockOut;
import cn.xinghuaerp.inventory.service.ErpStockInService;
import cn.xinghuaerp.inventory.service.ErpStockOutService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockFlowTools {

    private final ErpStockInService stockInService;
    private final ErpStockOutService stockOutService;
    public String getStockInList(
            Integer days,
            Integer stockInType,
            Integer limit) {
        if (days == null || days <= 0) days = 30;
        if (limit == null || limit <= 0) limit = 100;

        LambdaQueryWrapper<ErpStockIn> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(ErpStockIn::getCreateTime, LocalDateTime.now().minusDays(days));
        if (stockInType != null) {
            wrapper.eq(ErpStockIn::getStockInType, stockInType);
        }
        wrapper.orderByDesc(ErpStockIn::getCreateTime);
        wrapper.last("LIMIT " + limit);
        List<ErpStockIn> list = stockInService.list(wrapper);

        if (list.isEmpty()) {
            return "近" + days + "天无匹配的入库单记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(list.size()).append(" 条入库单记录（近").append(days).append("天）：\n");
        sb.append("| 入库单号 | 入库类型 | 来源单号 | 仓库 | 状态 | 入库人 | 入库时间 |\n");
        sb.append("|----------|---------|---------|------|------|--------|---------|\n");
        for (ErpStockIn s : list) {
            String typeText = switch (s.getStockInType() != null ? s.getStockInType() : 0) {
                case 1 -> "采购入库";
                case 2 -> "退货入库";
                default -> "其他";
            };
            String statusText = switch (s.getStatus() != null ? s.getStatus() : 0) {
                case 0 -> "待入库";
                case 1 -> "部分入库";
                case 2 -> "全部入库";
                default -> "未知";
            };
            String date = s.getStockInTime() != null
                    ? s.getStockInTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "-";
            sb.append("| ").append(s.getStockInNum() != null ? s.getStockInNum() : "-")
                    .append(" | ").append(typeText)
                    .append(" | ").append(s.getSourceNo() != null ? s.getSourceNo() : "-")
                    .append(" | ").append(s.getWarehouseName() != null ? s.getWarehouseName() : "-")
                    .append(" | ").append(statusText)
                    .append(" | ").append(s.getStockInOperator() != null ? s.getStockInOperator() : "-")
                    .append(" | ").append(date)
                    .append(" |\n");
        }

        return sb.toString();
    }
    public String getStockOutList(
            Integer days,
            Integer type,
            Integer limit) {
        if (days == null || days <= 0) days = 30;
        if (limit == null || limit <= 0) limit = 100;

        LambdaQueryWrapper<ErpStockOut> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(ErpStockOut::getCreateTime, LocalDateTime.now().minusDays(days));
        if (type != null) {
            wrapper.eq(ErpStockOut::getType, type);
        }
        wrapper.orderByDesc(ErpStockOut::getCreateTime);
        wrapper.last("LIMIT " + limit);
        List<ErpStockOut> list = stockOutService.list(wrapper);

        if (list.isEmpty()) {
            return "近" + days + "天无匹配的出库单记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(list.size()).append(" 条出库单记录（近").append(days).append("天）：\n");
        sb.append("| 出库单号 | 出库类型 | 来源单号 | 仓库 | 状态 | 出库人 | 出库时间 |\n");
        sb.append("|----------|---------|---------|------|------|--------|---------|\n");
        for (ErpStockOut s : list) {
            String typeText = switch (s.getType() != null ? s.getType() : 0) {
                case 1 -> "订单发货";
                case 2 -> "采购退货";
                case 3 -> "盘点出库";
                case 4 -> "报损出库";
                default -> "其他";
            };
            String statusText = switch (s.getStatus() != null ? s.getStatus() : 0) {
                case 0 -> "待出库";
                case 1 -> "部分出库";
                case 2 -> "全部出库";
                default -> "未知";
            };
            String date = s.getOutTime() != null
                    ? s.getOutTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "-";
            sb.append("| ").append(s.getOutNum() != null ? s.getOutNum() : "-")
                    .append(" | ").append(typeText)
                    .append(" | ").append(s.getSourceNum() != null ? s.getSourceNum() : "-")
                    .append(" | ").append(s.getWarehouseName() != null ? s.getWarehouseName() : "-")
                    .append(" | ").append(statusText)
                    .append(" | ").append(s.getOperatorName() != null ? s.getOperatorName() : "-")
                    .append(" | ").append(date)
                    .append(" |\n");
        }

        return sb.toString();
    }
}
