package cn.xinghuaerp.ai.vo;

import lombok.Data;

/** 可用良品为零的仓库与业务主体组合。 */
@Data
public class AiInventoryEmptyScope {
    private String skuId;
    private String warehouseId;
    private String merchantId;
    private String shopId;
    private Long availableQuantity;
}
