package cn.xinghuaerp.open.wei.model;

import tools.jackson.databind.JsonNode;
import lombok.Data;
import java.util.List;

@Data
public class OrderDetail {
    private List<OrderDetailProduct> product_infos;

    private JsonNode price_info;//	价格信息
    private JsonNode pay_info;//	支付信息
    private OrderDetailDeliveryInfo delivery_info;//	配送信息
    private JsonNode coupon_info;//	优惠券信息
    private JsonNode ext_info;//	额外信息
    private JsonNode commission_infos;//	分佣信息
    private JsonNode settle_info;//	结算信息
}
