package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.order.entity.ShopOrder;
import cn.xinghuaerp.order.service.ShopOrderService;
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
public class OrderTools {

    private final ShopOrderService orderService;
    public String getOrderList(Integer days, Integer orderStatus, Integer limit) {
        return getOrderList(days, orderStatus, limit, new cn.xinghuaerp.system.service.SalesScope(null, null));
    }
    public String getOrderList(
            Integer days,
            Integer orderStatus,
            Integer limit, cn.xinghuaerp.system.service.SalesScope scope) {
        if (days == null || days <= 0) days = 30;
        days = Math.min(days, 90);
        if (limit == null || limit <= 0) limit = 100;
        limit = Math.min(limit, 200);

        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<>();
        java.util.Objects.requireNonNull(scope, "缺少数据范围");
        wrapper.eq(scope.merchantId() != null, ShopOrder::getMerchantId, scope.merchantId());
        wrapper.eq(scope.shopId() != null, ShopOrder::getShopId, scope.shopId());
        wrapper.ge(ShopOrder::getCreateOn, LocalDateTime.now().minusDays(days));
        if (orderStatus != null) {
            wrapper.eq(ShopOrder::getOrderStatus, orderStatus);
        }
        // 先取匹配总数再取样本，避免把 limit 截断后的条数当成订单总量交给模型。
        long total = orderService.count(wrapper);
        wrapper.orderByDesc(ShopOrder::getCreateOn);
        wrapper.last("LIMIT " + limit);
        List<ShopOrder> list = orderService.list(wrapper);

        if (list.isEmpty()) {
            return "近" + days + "天无匹配的订单记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(total).append(" 条订单记录（近").append(days).append("天）");
        if (total > list.size()) {
            sb.append("，下列仅为按下单时间倒序的前 ").append(list.size())
                    .append(" 条，不是全部订单，回答时不要把它当作订单总量");
        }
        sb.append("：\n");
        sb.append("| 订单号 | 订单金额(分) | 支付金额(分) | 收件人 | 收货城市 | 订单状态 | 发货状态 | 下单时间 |\n");
        sb.append("|--------|-------------|-------------|--------|---------|---------|---------|---------|\n");
        for (ShopOrder o : list) {
            String statusText = switch (o.getOrderStatus() != null ? o.getOrderStatus() : -1) {
                case 0 -> "新订单";
                case 1 -> "待发货";
                case 2 -> "已发货";
                case 3 -> "已完成";
                case 11 -> "已取消";
                case 12 -> "退款中";
                case 13 -> "已关闭";
                case 21 -> "待付款";
                case 22 -> "锁定";
                case 29 -> "已删除";
                case 101 -> "部分发货";
                default -> "未知";
            };
            String shipText = switch (o.getErpShipStatus() != null ? o.getErpShipStatus() : -1) {
                case 0 -> "待发货";
                case 1 -> "部分发货";
                case 2 -> "已发货";
                default -> "未知";
            };
            // 地址只保留到城市：订单分析不需要门牌，避免把可定位到人的住址交给模型。
            String address = (o.getProvince() != null ? o.getProvince() : "")
                    + (o.getCity() != null ? o.getCity() : "");
            String date = o.getCreateOn() != null
                    ? o.getCreateOn().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "-";
            sb.append("| ").append(o.getOrderId() != null ? o.getOrderId() : "-")
                    .append(" | ").append(o.getOrderAmount() != null ? o.getOrderAmount() : 0)
                    .append(" | ").append(o.getPaymentAmount() != null ? o.getPaymentAmount() : 0)
                    .append(" | ").append(o.getReceiverName() != null ? o.getReceiverName() : "-")
                    .append(" | ").append(address.isBlank() ? "-" : address)
                    .append(" | ").append(statusText)
                    .append(" | ").append(shipText)
                    .append(" | ").append(date)
                    .append(" |\n");
        }

        return sb.toString();
    }
}
