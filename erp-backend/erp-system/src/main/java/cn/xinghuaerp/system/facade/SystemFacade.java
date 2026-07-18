package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.ErpLogisticsCompany;
import cn.xinghuaerp.system.entity.ErpMerchant;
import cn.xinghuaerp.system.entity.OShop;

/** 模块对外业务契约：跨模块只能通过本接口访问，禁止直接操作 Mapper。 */
public interface SystemFacade {
    ErpLogisticsCompany getErpLogisticsCompanyById(java.io.Serializable id);
    ErpMerchant getErpMerchantById(java.io.Serializable id);
    OShop getOShopById(java.io.Serializable id);
    java.util.List<OShop> listOShop(com.baomidou.mybatisplus.core.conditions.Wrapper<OShop> wrapper);
}
