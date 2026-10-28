package cn.xinghuaerp.inventory.facade;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.inventory.entity.OOrderStockingItem;
import cn.xinghuaerp.inventory.bo.ShipStockUpCompleteBo;
import cn.xinghuaerp.inventory.bo.StockingOrderItemBo;
import cn.xinghuaerp.inventory.bo.SupplierShipOrderItemListBo;

import java.util.List;

/**
* @author qilip
* @description 针对表【o_supplier_ship_order_item】的数据库操作Service
* @createDate 2025-02-18 13:48:13
*/
public interface OOrderStockingItemWorkflow {
    /**
     * 查询供应商备货清单
     * @param bo
     * @param pageQuery
     * @return
     */
    PageResult<OOrderStockingItem> queryVendorStockingPageList(SupplierShipOrderItemListBo bo, PageQuery pageQuery);

    /**
     * 导出excel数据
     * @param bo
     * @return
     */
    List<OOrderStockingItem> queryAllList2(SupplierShipOrderItemListBo bo );

    /**
     * 查询仓库备货清单
     * @param bo
     * @param pageQuery
     * @return
     */
    PageResult<OOrderStockingItem> queryStockingPageList(StockingOrderItemBo bo, PageQuery pageQuery);

    /**
     * 生成出库单()
     * @param bo
     * @return
     */
    ResultVo generateStockOutByItem(ShipStockUpCompleteBo bo);

    ResultVo generateStockOutByOrder(ShipStockUpCompleteBo bo);


    OOrderStockingItem getById(java.io.Serializable id);
    boolean save(OOrderStockingItem item);
    boolean updateById(OOrderStockingItem item);
    long count(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper);
    List<OOrderStockingItem> list(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrderStockingItem> wrapper);
}
