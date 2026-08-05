package cn.xinghuaerp.purchase.facade;

import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.purchase.entity.ErpSupplierCustomer;
import cn.xinghuaerp.purchase.entity.ErpSupplierProduct;

/** 模块对外业务契约：跨模块只能通过本接口访问，禁止直接操作 Mapper。 */
public interface PurchaseFacade {
    int saveErpSupplierCustomer(ErpSupplierCustomer entity);
    ErpSupplierCustomer getErpSupplierCustomerOne(com.baomidou.mybatisplus.core.conditions.Wrapper<ErpSupplierCustomer> wrapper);
    int updateErpSupplierCustomer(ErpSupplierCustomer entity);
    ErpSupplier getErpSupplierById(java.io.Serializable id);
    ErpSupplierProduct getErpSupplierProductById(java.io.Serializable id);
}
