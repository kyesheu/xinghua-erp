package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.order.entity.OOrderShipWaybill;
import cn.xinghuaerp.fulfillment.service.OOrderShipWaybillService;
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
public class LogisticsTools {

    private final OOrderShipWaybillService waybillService;
    public String getWaybillList(
            Integer days,
            Integer status,
            Integer limit) {
        if (days == null || days <= 0) days = 30;
        if (limit == null || limit <= 0) limit = 100;

        LambdaQueryWrapper<OOrderShipWaybill> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(OOrderShipWaybill::getCreateTime, LocalDateTime.now().minusDays(days));
        if (status != null) {
            wrapper.eq(OOrderShipWaybill::getStatus, status);
        }
        wrapper.orderByDesc(OOrderShipWaybill::getCreateTime);
        wrapper.last("LIMIT " + limit);
        List<OOrderShipWaybill> list = waybillService.list(wrapper);

        if (list.isEmpty()) {
            return "近" + days + "天无匹配的物流运单记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(list.size()).append(" 条运单记录（近").append(days).append("天）：\n");
        sb.append("| 快递单号 | 快递公司 | 订单号 | 店铺ID | 状态 | 创建时间 |\n");
        sb.append("|----------|---------|--------|--------|------|---------|\n");
        for (OOrderShipWaybill w : list) {
            String statusText = switch (w.getStatus() != null ? w.getStatus() : 0) {
                case 1 -> "已取号";
                case 2 -> "已打印";
                case 3 -> "已发货";
                default -> "未知";
            };
            String date = w.getCreateTime() != null
                    ? w.getCreateTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "-";
            sb.append("| ").append(w.getWaybillCode() != null ? w.getWaybillCode() : "-")
                    .append(" | ").append(w.getLogisticsCode() != null ? w.getLogisticsCode() : "-")
                    .append(" | ").append(w.getOrderId() != null ? w.getOrderId() : "-")
                    .append(" | ").append(w.getShopId() != null ? w.getShopId() : "-")
                    .append(" | ").append(statusText)
                    .append(" | ").append(date)
                    .append(" |\n");
        }

        return sb.toString();
    }
}
