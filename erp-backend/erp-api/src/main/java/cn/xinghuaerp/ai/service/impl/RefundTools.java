package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.order.entity.ShopRefund;
import cn.xinghuaerp.order.service.ShopRefundService;
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
public class RefundTools {

    private final ShopRefundService refundService;
    public String getRefundList(Integer days, String reason, String goodsName, Integer limit) {
        return getRefundList(days, reason, goodsName, limit, new cn.xinghuaerp.system.service.SalesScope(null, null));
    }
    public String getRefundList(
            Integer days,
            String reason,
            String goodsName,
            Integer limit, cn.xinghuaerp.system.service.SalesScope scope) {
        if (days == null || days <= 0) days = 30;
        days = Math.min(days, 90);
        if (limit == null || limit <= 0) limit = 200;

        LambdaQueryWrapper<ShopRefund> wrapper = new LambdaQueryWrapper<>();
        java.util.Objects.requireNonNull(scope, "缺少数据范围");
        wrapper.eq(scope.merchantId() != null, ShopRefund::getMerchantId, scope.merchantId());
        wrapper.eq(scope.shopId() != null, ShopRefund::getShopId, scope.shopId());
        wrapper.ge(ShopRefund::getCreateOn, LocalDateTime.now().minusDays(days));
        if (reason != null && !reason.isBlank()) {
            wrapper.like(ShopRefund::getReason, reason);
        }
        if (goodsName != null && !goodsName.isBlank()) {
            wrapper.like(ShopRefund::getGoodsName, goodsName);
        }
        wrapper.orderByDesc(ShopRefund::getCreateOn);
        wrapper.last("LIMIT " + limit);
        List<ShopRefund> list = refundService.list(wrapper);

        if (list.isEmpty()) {
            return "近" + days + "天无匹配的退款记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(list.size()).append(" 条退款记录（近").append(days).append("天）：\n");
        sb.append("| 商品名 | 退款原因 | 退款金额(分) | 申请时间 | 售后状态 |\n");
        sb.append("|--------|---------|-------------|---------|---------|\n");
        for (ShopRefund r : list) {
            String date = r.getCreateOn() != null
                    ? r.getCreateOn().format(DateTimeFormatter.ofPattern("MM-dd"))
                    : "-";
            String statusText = switch (r.getStatus() != null ? r.getStatus() : -1) {
                case 0 -> "售后申请";
                case 1 -> "已关闭";
                case 2 -> "处理中";
                case 3 -> "退款中";
                case 4 -> "售后成功";
                default -> "未知";
            };
            sb.append("| ").append(r.getGoodsName() != null ? r.getGoodsName() : "-")
                    .append(" | ").append(r.getReason() != null ? r.getReason() : "-")
                    .append(" | ").append(r.getRefundAmount() != null ? r.getRefundAmount() : 0)
                    .append(" | ").append(date)
                    .append(" | ").append(statusText)
                    .append(" |\n");
        }

        return sb.toString();
    }
}
