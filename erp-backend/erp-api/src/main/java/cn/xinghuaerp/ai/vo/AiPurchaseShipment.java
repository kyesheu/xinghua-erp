package cn.xinghuaerp.ai.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** Registered order-level logistics; not a SKU shipment quantity or arrival promise. */
@Data
public class AiPurchaseShipment {
    private String id;
    private String orderId;
    private String orderNum;
    private String warehouseId;
    private String shipCompany;
    private String shipNum;
    private LocalDateTime shipTime;
    private LocalDateTime receiptTime;
    private Integer status;
}
