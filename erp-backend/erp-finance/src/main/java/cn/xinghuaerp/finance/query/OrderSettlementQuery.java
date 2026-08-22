package cn.xinghuaerp.finance.query;

import lombok.Data;

@Data
public class OrderSettlementQuery {
    private Long merchantId;
    private Long shopId;
    private String orderNo;
    private Integer status;
    private String platform;
}
