package cn.xinghuaerp.open.wei.model;

import tools.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ProductSku {
    private String sku_id;
    private String out_sku_id;
    private String thumb_img;
    private String sku_code;
    private JsonNode sku_attrs;
    private Integer sale_price;
    private Integer stock_num;
    private Integer status;
    private JsonNode sku_deliver_info;

}
