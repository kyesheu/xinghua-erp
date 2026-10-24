package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.purchase.service.ErpSupplierService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierTools {

    private final ErpSupplierService supplierService;
    public String getSupplierList(
            String keyword,
            Integer limit) {
        if (limit == null || limit <= 0) limit = 50;

        LambdaQueryWrapper<ErpSupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ErpSupplier::getIsDelete, 0);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ErpSupplier::getName, keyword);
        }
        wrapper.orderByAsc(ErpSupplier::getName);
        wrapper.last("LIMIT " + limit);
        List<ErpSupplier> list = supplierService.list(wrapper);

        if (list.isEmpty()) {
            return "无匹配的供应商记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(list.size()).append(" 条供应商记录：\n");
        sb.append("| 供应商名 | 联系人 | 联系电话 | 地址 |\n");
        sb.append("|----------|--------|---------|------|\n");
        for (ErpSupplier s : list) {
            String address = (s.getProvince() != null ? s.getProvince() : "")
                    + (s.getCity() != null ? s.getCity() : "")
                    + (s.getCounty() != null ? s.getCounty() : "")
                    + (s.getAddress() != null ? s.getAddress() : "");
            sb.append("| ").append(s.getName() != null ? s.getName() : "-")
                    .append(" | ").append(s.getLinkMan() != null ? s.getLinkMan() : "-")
                    .append(" | ").append(s.getContact() != null ? s.getContact() : "-")
                    .append(" | ").append(address.isBlank() ? "-" : address)
                    .append(" |\n");
        }

        return sb.toString();
    }
}
