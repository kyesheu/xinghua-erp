package cn.xinghuaerp.inventory.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 
 *
 */
@Data
public class WarehouseAddRequest implements Serializable {

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

    private String province;
    private String city;
    private String county;
    private String address;
    private String remark;

}