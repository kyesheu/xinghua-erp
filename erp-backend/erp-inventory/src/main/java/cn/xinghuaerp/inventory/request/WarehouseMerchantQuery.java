package cn.xinghuaerp.inventory.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 
 *
 */
@Data
public class WarehouseMerchantQuery implements Serializable {



    /**
     * 状态（1启用0禁用）
     */
    private String status;
    /**
     * 商户ID
     */
    private Long merchantId;
    /**
     * 店铺ID
     */
    private Long shopId;

}