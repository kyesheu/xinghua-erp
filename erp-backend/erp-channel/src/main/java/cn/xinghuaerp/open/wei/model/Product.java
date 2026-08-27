package cn.xinghuaerp.open.wei.model;

import tools.jackson.databind.JsonNode;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class Product {
    private String product_id;
    private String out_product_id;
    private String title;
    private String sub_title;
    private JsonNode head_imgs;
    private JsonNode desc_info;
    private List<Map<String,String>> attrs;
    private Integer status;
    private Integer edit_status;
    private Integer min_price;
    private String spu_code;
    private List<ProductSku> skus;
    private Integer product_type;
    private String edit_time;
}
