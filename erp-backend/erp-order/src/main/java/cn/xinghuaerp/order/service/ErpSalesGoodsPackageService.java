package cn.xinghuaerp.order.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.order.entity.ErpSalesGoodsPackage;
import cn.xinghuaerp.order.entity.ErpSalesGoodsPackageItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ErpSalesGoodsPackageService extends IService<ErpSalesGoodsPackage> {
    PageResult<ErpSalesGoodsPackage> queryPageList(PageQuery pageQuery);

    boolean addPackage(ErpSalesGoodsPackage pkg);

    Long addPackageAndGetId(ErpSalesGoodsPackage pkg);

    boolean updatePackage(ErpSalesGoodsPackage pkg);

    boolean deletePackage(Long id);

    ErpSalesGoodsPackage getPackageById(Long id);

    List<ErpSalesGoodsPackageItem> getPackageItems(Long packageId);

    boolean savePackageItems(Long packageId, List<ErpSalesGoodsPackageItem> items);
}