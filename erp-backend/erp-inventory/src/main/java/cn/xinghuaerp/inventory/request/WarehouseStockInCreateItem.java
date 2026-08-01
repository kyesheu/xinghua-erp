package cn.xinghuaerp.inventory.request;

import lombok.Data;

@Data
public class WarehouseStockInCreateItem {
    private Long id;
    private String erpGoodsNo;
    private String goodsName;
    private Integer quantity;
    private String goodsNo;
    private String imageUrl;
    private String standard;

}
