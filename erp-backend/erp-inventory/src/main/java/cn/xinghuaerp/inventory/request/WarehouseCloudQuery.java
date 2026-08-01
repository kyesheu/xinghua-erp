package cn.xinghuaerp.inventory.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 
 *
 */
@Data
public class WarehouseCloudQuery implements Serializable {

    /**
     * 类型（LOCAL本地仓CLOUD系统云仓JDYC京东云仓Other其他）
     */
    private String warehouseType;

    /**
     * status
     */
    private String status;

    private Long merchantId;

}