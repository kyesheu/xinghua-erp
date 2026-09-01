package cn.xinghuaerp.ai.vo;

import lombok.Data;

/** 采购明细与全部入库任务核对；只有关联完整且数量一致时输出未入库量。 */
@Data
public class AiPurchaseLedgerRow {
    private String orderId;
    private String orderNum;
    private String itemId;
    private Integer orderStatus;
    private String warehouseId;
    private String merchantId;
    private String shopId;
    private Long orderedQuantity;
    private Long taskCount;
    private Long taskQuantity;
    private Long receivedQuantity;
    private Long invalidTasks;
    private Long entryCount;
    private Long remainingQuantity;
    private String checkStatus;
}
