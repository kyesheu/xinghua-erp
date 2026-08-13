package cn.xinghuaerp.order.request;

import lombok.Data;
import java.math.BigDecimal;

/** 本地售后请求：金额单位元，所有订单/SKU归属与数量均由后端校验。 */
@Data
public class LocalAfterSaleRequest {
    private Long shopId;
    private Long orderItemId;
    private Integer type;
    private Integer quantity;
    private BigDecimal refundAmount;
    private Long exchangeSkuId;
    private String reason;
    private String refundNum;
    private Long returnWarehouseId;
    /** 0退回本地仓，300退回原发货供应商。 */
    private Integer returnType;
    private Long returnSupplierId;
    /** 人工确认供应商已收到退货；不调用供应商接口。 */
    private Boolean returnReceived;
    private Long warehouseId;
    /** 0本地仓补发，300供应商补发。 */
    private Integer reissueType;
    private Long reissueSupplierId;
    private String returnLogisticsCompany;
    private String returnLogisticsCode;
    private Long shippingCompanyId;
    private String shippingNumber;
    private String receiverName;
    private String receiverTel;
    private String receiverAddress;
    private String remark;
    /** 人工确认已在原收款渠道完成退款；此字段不会触发实际支付。 */
    private Boolean refundPaid;
}
