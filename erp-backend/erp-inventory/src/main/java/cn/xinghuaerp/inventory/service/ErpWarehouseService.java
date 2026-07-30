package cn.xinghuaerp.inventory.service;

import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.inventory.request.WarehouseCloudQuery;
import cn.xinghuaerp.inventory.request.WarehousePageQuery;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author qilip
* @description 针对表【erp_cloud_warehouse】的数据库操作Service
* @createDate 2025-07-07 15:10:27
*/
public interface ErpWarehouseService extends IService<ErpWarehouse> {
    PageResult<ErpWarehouse> queryPageList(WarehousePageQuery query );

    List<ErpWarehouse> getCloudWarehouseList(WarehouseCloudQuery query);
    List<ErpWarehouse> getJdWarehouseList(ErpWarehouse bo);
    List<ErpWarehouse> getJkyWarehouseList(ErpWarehouse bo);
    List<ErpWarehouse> getWarehouseList(ErpWarehouse bo);
    ResultVo<Long> saveErpCloudWarehouse(List<ErpWarehouse> list);
    /**
     * 为供应商创建配套仓库（幂等，按供应商ID生成仓库编号）。
     *
     * @param supplierId   供应商ID
     * @param supplierName 供应商名称
     * @param username     操作人
     * @return 仓库ID
     */
    ResultVo<Long> createSupplierWarehouse(Long supplierId, String supplierName, String username);
}
