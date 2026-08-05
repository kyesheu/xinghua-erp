package cn.xinghuaerp.purchase.facade;

import cn.xinghuaerp.purchase.mapper.ErpSupplierCustomerMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.purchase.entity.ErpSupplierCustomer;
import cn.xinghuaerp.purchase.entity.ErpSupplierProduct;

@Component
@RequiredArgsConstructor
public class PurchaseFacadeImpl implements PurchaseFacade {
    private final ErpSupplierCustomerMapper erpSupplierCustomerMapper;
    private final ErpSupplierMapper erpSupplierMapper;
    private final ErpSupplierProductMapper erpSupplierProductMapper;

    @Override public int saveErpSupplierCustomer(ErpSupplierCustomer entity) { return erpSupplierCustomerMapper.insert(entity); }
    @Override public ErpSupplierCustomer getErpSupplierCustomerOne(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSupplierCustomer> wrapper) { return erpSupplierCustomerMapper.selectOne(wrapper); }
    @Override public int updateErpSupplierCustomer(ErpSupplierCustomer entity) { return erpSupplierCustomerMapper.updateById(entity); }
    @Override public ErpSupplier getErpSupplierById(java.io.Serializable id) { return erpSupplierMapper.selectById(id); }
    @Override public ErpSupplierProduct getErpSupplierProductById(java.io.Serializable id) { return erpSupplierProductMapper.selectById(id); }
}
