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

        LambdaQueryWrapper<ShopMember> wrapper = new LambdaQueryWrapper<>();
        java.util.Objects.requireNonNull(scope, "缺少数据范围");
        wrapper.eq(scope.merchantId() != null, ShopMember::getMerchantId, scope.merchantId());
        wrapper.eq(scope.shopId() != null, ShopMember::getShopId, scope.shopId());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ShopMember::getName, keyword)
                    .or().like(ShopMember::getPhone, keyword)
                    .or().like(ShopMember::getPlatformAccount, keyword));
        }
        wrapper.orderByDesc(ShopMember::getCreateOn);
        wrapper.last("LIMIT " + limit);
        List<ShopMember> list = memberService.list(wrapper);

        if (list.isEmpty()) {
            return "无匹配的会员记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(list.size()).append(" 条会员记录：\n");
        sb.append("| 姓名 | 手机号 | 收货地址 | 平台 | 创建时间 |\n");
        sb.append("|------|--------|---------|------|---------|\n");
        for (ShopMember m : list) {
            String address = (m.getProvince() != null ? m.getProvince() : "")
                    + (m.getCity() != null ? m.getCity() : "")
                    + (m.getCounty() != null ? m.getCounty() : "")
                    + (m.getAddress() != null ? m.getAddress() : "");
            String date = m.getCreateOn() != null
                    ? m.getCreateOn().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "-";
            sb.append("| ").append(m.getName() != null ? m.getName() : "-")
                    .append(" | ").append(m.getPhone() != null ? m.getPhone() : "-")
                    .append(" | ").append(address.isBlank() ? "-" : address)
                    .append(" | ").append(m.getShopType() != null ? m.getShopType() : "-")
                    .append(" | ").append(date)
                    .append(" |\n");
        }

        return sb.toString();
    }
}
