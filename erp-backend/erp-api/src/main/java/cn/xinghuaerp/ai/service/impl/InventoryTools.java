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
    public String getInventoryList(
            Integer availableLte,
            Integer limit) {
        if (limit == null || limit <= 0) limit = 100;

        LambdaQueryWrapper<OGoodsInventory> wrapper = new LambdaQueryWrapper<>();
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
