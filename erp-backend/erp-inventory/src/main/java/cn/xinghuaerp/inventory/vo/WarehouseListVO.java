package cn.xinghuaerp.inventory.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 
 *
 */
@Data
public class WarehouseListVO implements Serializable {
    /**
     * 
     */
    private Long id;

    /**
     * 拥有者
     */
    private String ownerNo;

    /**
     * 类型（1本地仓2云仓）
     */
    private Integer type;
    /**
     * 类型（LOCAL本地仓CLOUD系统云仓JDYC京东云仓Other其他）
     */
    private String warehouseType;
    private String loginName;

    /**
     * 云仓编码
     */
    private String warehouseNo;

    /**
     * 名称
     */
    private String warehouseName;
    private Integer warehouseSource;

    private String erpWarehouseName;


//    /**
//     * type
//     */
//    private String type;

    /**
     * status
     */
    private String status;

    /**
     * 省
     */
    private String province;

    /**
     * 市
     */
    private String city;

    /**
     * 区
     */
    private String county;

    /**
     * 街道
     */
    private String town;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 联系人
     */
    private String contacts;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    private Long merchantId;
    private String remark;
    private Integer jdlApiType;
    private String merchantIds;
}