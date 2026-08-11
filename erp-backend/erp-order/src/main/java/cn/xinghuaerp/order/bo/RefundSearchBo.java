package cn.xinghuaerp.order.bo;

import lombok.Data;

@Data
public class RefundSearchBo {
    private Long shopId;
    private Long merchantId;
    private Integer status;
    private String orderNum;
    private Integer refundType;
    private String refundNum;
    private Integer erpPushStatus;
    private Integer hasProcessing;
    private Long supplierId;
}
