package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.mapper.ErpLogisticsCompanyMapper;
import cn.xinghuaerp.system.mapper.ErpMerchantMapper;
import cn.xinghuaerp.system.mapper.OShopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import cn.xinghuaerp.system.entity.ErpLogisticsCompany;
import cn.xinghuaerp.system.entity.ErpMerchant;
import cn.xinghuaerp.system.entity.OShop;

@Component
@RequiredArgsConstructor
public class SystemFacadeImpl implements SystemFacade {
    private final ErpLogisticsCompanyMapper erpLogisticsCompanyMapper;
    private final ErpMerchantMapper erpMerchantMapper;
    private final OShopMapper oShopMapper;

    @Override public ErpLogisticsCompany getErpLogisticsCompanyById(java.io.Serializable id) { return erpLogisticsCompanyMapper.selectById(id); }
    @Override public ErpMerchant getErpMerchantById(java.io.Serializable id) { return erpMerchantMapper.selectById(id); }
    @Override public OShop getOShopById(java.io.Serializable id) { return oShopMapper.selectById(id); }
    @Override public java.util.List<OShop> listOShop(com.baomidou.mybatisplus.core.conditions.Wrapper<OShop> wrapper) { return oShopMapper.selectList(wrapper); }
}
