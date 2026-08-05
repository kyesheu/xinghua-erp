package cn.xinghuaerp.purchase.service.impl;

import cn.xinghuaerp.purchase.entity.ErpPurchaseLogistics;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseLogisticsMapper;
import cn.xinghuaerp.purchase.service.ErpPurchaseLogisticsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.springframework.stereotype.Service;

/**
* @author qilip
* @description 针对表【erp_purchase_logistics(采购承运商/物流公司表)】的数据库操作Service实现
* @createDate 2024-12-08 18:17:15
*/
@Service
public class ErpPurchaseLogisticsServiceImpl extends ServiceImpl<ErpPurchaseLogisticsMapper, ErpPurchaseLogistics>
    implements ErpPurchaseLogisticsService {

    @Override
    public int updateStatus(Long id, Integer status) {
        ErpPurchaseLogistics update = new ErpPurchaseLogistics();
        update.setId(id);
        update.setStatus(status);
        return this.baseMapper.updateById(update);
    }

    @Override
    public boolean existsByCode(String code, Long excludeId) {
        LambdaQueryWrapper<ErpPurchaseLogistics> query = new LambdaQueryWrapper<ErpPurchaseLogistics>()
                .eq(ErpPurchaseLogistics::getCode, code)
                .ne(excludeId != null, ErpPurchaseLogistics::getId, excludeId);
        return this.count(query) > 0;
    }
}
