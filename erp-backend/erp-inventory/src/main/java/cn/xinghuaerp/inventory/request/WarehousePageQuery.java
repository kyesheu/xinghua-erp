package cn.xinghuaerp.inventory.request;

import cn.xinghuaerp.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class WarehousePageQuery extends PageQuery {

    /**
     * 类型（LOCAL本地仓CLOUD系统云仓JDYC京东云仓Other其他）
     */
    private String warehouseType;

    /**
     * 云仓编码
     */
    private String warehouseNo;

    /**
     * 云仓名称
     */
    private String warehouseName;


    /**
     * status
     */
    private String status;

    private Long merchantId;
    private Long shopId;

}