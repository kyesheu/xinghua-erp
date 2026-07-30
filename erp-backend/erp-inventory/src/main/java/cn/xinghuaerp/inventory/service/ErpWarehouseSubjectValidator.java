package cn.xinghuaerp.inventory.service;

import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.inventory.mapper.ErpWarehouseMapper;
import cn.xinghuaerp.system.service.BusinessSubjectValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 仓库业务主体的可用性校验，供系统模块解析数据范围时回调。 */
@Component
@RequiredArgsConstructor
public class ErpWarehouseSubjectValidator implements BusinessSubjectValidator {

    private final ErpWarehouseMapper warehouseMapper;

    @Override
    public String subjectType() {
        return "WAREHOUSE";
    }

    @Override
    public void requireActive(Long subjectId) {
        ErpWarehouse warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<ErpWarehouse>()
                .select(ErpWarehouse::getId, ErpWarehouse::getStatus)
                .eq(ErpWarehouse::getId, subjectId));
        if (warehouse == null || !"1".equals(warehouse.getStatus())) {
            throw new IllegalArgumentException("仓库不存在或已停用");
        }
    }
}
