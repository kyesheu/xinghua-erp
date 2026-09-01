package cn.xinghuaerp.ai.vo;

import lombok.Data;

@Data
public class AiInventoryBatchAudit {
    private Long inventoryId;
    private Long batchCount;
    private Long batchQuantity;
    private Long invalidBatches;
}
