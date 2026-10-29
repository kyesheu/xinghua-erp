package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryTools {

    private final OGoodsInventoryService inventoryService;
    public String getInventoryList(Integer availableLte, Integer limit) {
        return getInventoryList(availableLte, limit, new cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope("HEADQUARTERS", null, null, null));
    }
    public String getInventoryList(
            Integer availableLte,
            Integer limit, cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope scope) {
        if (limit == null || limit <= 0) limit = 100;

        LambdaQueryWrapper<OGoodsInventory> wrapper = new LambdaQueryWrapper<>();
        java.util.Objects.requireNonNull(scope, "缺少数据范围");
        switch (scope.type()) {
            case "HEADQUARTERS" -> { }
            case "MERCHANT", "SHOP" -> {
                if (scope.merchantId() == null || ("SHOP".equals(scope.type()) && scope.shopId() == null))
                    throw new IllegalArgumentException("业务范围无效");
                wrapper.eq(OGoodsInventory::getMerchantId, scope.merchantId());
                wrapper.eq(scope.shopId() != null, OGoodsInventory::getShopId, scope.shopId());
            }
            case "WAREHOUSE" -> {
                if (scope.subjectId() == null) throw new IllegalArgumentException("仓库范围无效");
                wrapper.eq(OGoodsInventory::getWarehouseId, scope.subjectId());
            }
            default -> throw new org.springframework.security.access.AccessDeniedException("无库存访问范围");
        }
        wrapper.eq(OGoodsInventory::getIsDelete, 0);
        if (availableLte != null) {
            wrapper.le(OGoodsInventory::getAvailableQuantity, availableLte);
        }
        wrapper.orderByAsc(OGoodsInventory::getAvailableQuantity);
        wrapper.last("LIMIT " + limit);
        List<OGoodsInventory> list = inventoryService.list(wrapper);

        if (list.isEmpty()) {
            return "暂无匹配的库存记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(list.size()).append(" 条库存记录：\n");
        sb.append("| SKU名称 | 编码 | 可用库存 | 仓库ID |\n");
        sb.append("|---------|------|---------|-------|\n");
        for (OGoodsInventory inv : list) {
            sb.append("| ").append(inv.getSkuName() != null ? inv.getSkuName() : "-")
                    .append(" | ").append(inv.getSkuCode() != null ? inv.getSkuCode() : "-")
                    .append(" | ").append(inv.getAvailableQuantity())
                    .append(" | ").append(inv.getWarehouseId() != null ? inv.getWarehouseId() : "-")
                    .append(" |\n");
        }

        return sb.toString();
    }
}
