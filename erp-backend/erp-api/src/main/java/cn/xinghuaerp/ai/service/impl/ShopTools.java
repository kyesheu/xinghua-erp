package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.system.service.OShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopTools {

    private final OShopService shopService;
    public String getShopList(cn.xinghuaerp.system.service.SalesScope scope) {
        java.util.Objects.requireNonNull(scope, "缺少数据范围");
        var query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.xinghuaerp.system.entity.OShop>();
        query.eq(scope.merchantId() != null, cn.xinghuaerp.system.entity.OShop::getMerchantId, scope.merchantId());
        query.eq(scope.shopId() != null, cn.xinghuaerp.system.entity.OShop::getId, scope.shopId());
        query.orderByAsc(cn.xinghuaerp.system.entity.OShop::getId).last("LIMIT 100");
        var shops = shopService.list(query);
        if (shops == null || shops.isEmpty()) {
            return "暂无店铺数据";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(shops.size()).append(" 个店铺：\n");
        for (var shop : shops) {
            sb.append("  - ").append(shop.getName() != null ? shop.getName() : "")
                    .append(" [平台类型:").append(shop.getType() != null ? shop.getType() : "").append("]\n");
        }
        return sb.toString();
    }
}
