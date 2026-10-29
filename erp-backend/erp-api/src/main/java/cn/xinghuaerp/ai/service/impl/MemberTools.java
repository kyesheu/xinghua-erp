package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.order.entity.ShopMember;
import cn.xinghuaerp.order.service.ShopMemberService;
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
public class MemberTools {

    private final ShopMemberService memberService;
    public String getMemberList(String keyword, Integer limit) {
        return getMemberList(keyword, limit, new cn.xinghuaerp.system.service.SalesScope(null, null));
    }
    public String getMemberList(
            String keyword,
            Integer limit, cn.xinghuaerp.system.service.SalesScope scope) {
        if (limit == null || limit <= 0) limit = 50;
        limit = Math.min(limit, 100);

        LambdaQueryWrapper<ShopMember> wrapper = new LambdaQueryWrapper<>();
        java.util.Objects.requireNonNull(scope, "缺少数据范围");
        wrapper.eq(scope.merchantId() != null, ShopMember::getMerchantId, scope.merchantId());
        wrapper.eq(scope.shopId() != null, ShopMember::getShopId, scope.shopId());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ShopMember::getName, keyword)
                    .or().like(ShopMember::getPhone, keyword)
                    .or().like(ShopMember::getPlatformAccount, keyword));
        }
        // 先取匹配总数再取样本，避免把 limit 截断后的条数当成会员总量交给模型。
        long total = memberService.count(wrapper);
        wrapper.orderByDesc(ShopMember::getCreateOn);
        wrapper.last("LIMIT " + limit);
        List<ShopMember> list = memberService.list(wrapper);

        if (list.isEmpty()) {
            return "无匹配的会员记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(total).append(" 条会员记录");
        if (total > list.size()) {
            sb.append("，下列仅为按创建时间倒序的前 ").append(list.size())
                    .append(" 条，不是全部会员，回答时不要把它当作会员总量");
        }
        sb.append("；手机号与地址已脱敏，模型不得据此推断个人身份：\n");
        sb.append("| 姓名 | 手机号 | 所在城市 | 平台 | 创建时间 |\n");
        sb.append("|------|--------|---------|------|---------|\n");
        for (ShopMember m : list) {
            String city = (m.getProvince() != null ? m.getProvince() : "")
                    + (m.getCity() != null ? m.getCity() : "");
            String date = m.getCreateOn() != null
                    ? m.getCreateOn().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "-";
            sb.append("| ").append(m.getName() != null ? m.getName() : "-")
                    .append(" | ").append(maskPhone(m.getPhone()))
                    .append(" | ").append(city.isBlank() ? "-" : city)
                    .append(" | ").append(m.getShopType() != null ? m.getShopType() : "-")
                    .append(" | ").append(date)
                    .append(" |\n");
        }

        return sb.toString();
    }

    /** AI 侧只需要能定位会员，不需要完整手机号；保留前三后四便于人工核对。 */
    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "-";
        String trimmed = phone.trim();
        if (trimmed.length() < 7) return "***";
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
