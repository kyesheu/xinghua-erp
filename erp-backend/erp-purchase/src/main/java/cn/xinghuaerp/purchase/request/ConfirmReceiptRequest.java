package cn.xinghuaerp.purchase.request;

import lombok.Data;

@Data
public class ConfirmReceiptRequest {
    private Long id;
    private Long orderId;
    private String receiptTime;
    private String remark;
}
