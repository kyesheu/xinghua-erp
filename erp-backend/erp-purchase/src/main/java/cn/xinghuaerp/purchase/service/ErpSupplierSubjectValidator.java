package cn.xinghuaerp.purchase.service;

import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.system.service.BusinessSubjectValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 供应商业务主体的可用性校验，供系统模块解析数据范围时回调。 */
@Component
@RequiredArgsConstructor
public class ErpSupplierSubjectValidator implements BusinessSubjectValidator {

    private final ErpSupplierMapper supplierMapper;

    @Override
    public String subjectType() {
        return "SUPPLIER";
    }

    @Override
    public void requireActive(Long subjectId) {
        ErpSupplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<ErpSupplier>()
                .select(ErpSupplier::getId, ErpSupplier::getDisable, ErpSupplier::getIsDelete)
                .eq(ErpSupplier::getId, subjectId));
        if (supplier == null || !Objects.equals(supplier.getDisable(), 0) || !Objects.equals(supplier.getIsDelete(), 0)) {
            throw new IllegalArgumentException("供应商不存在或已停用");
        }
    }
}
