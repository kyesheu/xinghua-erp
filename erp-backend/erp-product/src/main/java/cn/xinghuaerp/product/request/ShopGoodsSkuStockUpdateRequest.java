package cn.xinghuaerp.product.request;

import lombok.Data;

@Data
public class ShopGoodsSkuStockUpdateRequest {
    private Long id;
    private Integer stockNum;
}
