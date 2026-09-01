package cn.xinghuaerp.ai.vo;

import lombok.Data;
import java.util.List;

/** 批量库存扫描结果；数量按授权范围的良品库存汇总。 */
@Data
public class AiInventoryOverviewRow {
    private String skuId;
    private String skuCode;
    private String goodsName;
    private String skuName;
    private Integer productStatus;
    private Integer lowQty;
    private Long inventoryRows;
    private Long goodRows;
    private Long distinctGoodScopes;
    private Long distinctWarehouses;
    private Long availableQuantity;
    private Long quantity;
    private Long lockedQuantity;
    private Long invalidRows;
    private Boolean emptyInventoryScope;
    private Boolean batchIssue;
    private List<AiInventoryEmptyScope> emptyScopes;
    /** 仅有销售查询权限且可确定业务主体时提供；null 表示不可用。 */
    private Long sales30Days;
    private Long openPurchaseOrders;
    private String risk;
}
