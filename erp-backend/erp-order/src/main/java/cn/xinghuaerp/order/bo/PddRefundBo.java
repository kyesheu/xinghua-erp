package cn.xinghuaerp.order.bo;

import lombok.Data;

@Data
public class PddRefundBo {
    private Integer shopId;
    private String refundId;
    private String orderSn;
    private String afterSalesType;
    private Long merchantId;
}
