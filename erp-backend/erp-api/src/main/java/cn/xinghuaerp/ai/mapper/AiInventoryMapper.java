package cn.xinghuaerp.ai.mapper;

import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.ai.vo.AiInventoryOverviewRow;
import cn.xinghuaerp.ai.vo.AiInventoryEmptyScope;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import cn.xinghuaerp.ai.vo.AiInventoryBatchAudit;
import cn.xinghuaerp.ai.vo.AiPurchaseLedgerRow;
import cn.xinghuaerp.ai.vo.AiPurchaseShipment;

/** AI 只读库存查询；范围由服务端解析，不能由客户端决定。 */
public interface AiInventoryMapper {
    List<AiInventoryOverviewRow> allOverview(@Param("merchantId") Long merchantId, @Param("shopId") Long shopId, @Param("warehouseId") Long warehouseId);
    List<OGoodsInventory> find(@Param("skuId") long skuId, @Param("warehouseId") Long warehouseId,
                               @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<cn.xinghuaerp.ai.vo.AiInventoryBatchAudit> batchAudit(@Param("inventoryIds") List<Long> inventoryIds);
    long salesQuantity(@Param("skuId") long skuId, @Param("start") LocalDateTime start,
                       @Param("end") LocalDateTime end, @Param("merchantId") Long merchantId,
                       @Param("shopId") Long shopId);
    List<AiInventoryOverviewRow> overviewSales(@Param("skuIds") List<Long> skuIds, @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end, @Param("merchantId") Long merchantId,
                                              @Param("shopId") Long shopId);
    List<AiInventoryEmptyScope> emptyScopes(@Param("skuIds") List<Long> skuIds, @Param("merchantId") Long merchantId,
                                          @Param("shopId") Long shopId, @Param("warehouseId") Long warehouseId);
    List<AiInventoryOverviewRow> overviewPurchases(@Param("skuIds") List<Long> skuIds,
                                                    @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<Map<String,Object>> purchaseInbound(@Param("skuId") long skuId, @Param("warehouseId") Long warehouseId,
                                            @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<Map<String,Object>> purchaseWithoutTask(@Param("skuId") long skuId,
                                               @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<cn.xinghuaerp.ai.vo.AiPurchaseLedgerRow> purchaseLedger(@Param("skuId") long skuId,
                                               @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<cn.xinghuaerp.ai.vo.AiPurchaseShipment> purchaseShipments(@Param("skuId") long skuId,
            @Param("warehouseId") Long warehouseId, @Param("merchantId") Long merchantId, @Param("shopId") Long shopId);
    List<OGoodsInventory> search(@Param("keyword") String keyword, @Param("merchantId") Long merchantId,
                                 @Param("shopId") Long shopId);
    long countOverview(@Param("merchantId") Long merchantId, @Param("shopId") Long shopId, @Param("warehouseId") Long warehouseId);
    List<AiInventoryOverviewRow> overview(@Param("merchantId") Long merchantId, @Param("shopId") Long shopId,
                                         @Param("warehouseId") Long warehouseId, @Param("offset") long offset, @Param("limit") int limit);

    long countProductOverview(@Param("merchantId") Long merchantId, @Param("shopId") Long shopId, @Param("warehouseId") Long warehouseId, @Param("keyword") String keyword);
    List<AiInventoryOverviewRow> productOverview(@Param("merchantId") Long merchantId, @Param("shopId") Long shopId, @Param("warehouseId") Long warehouseId, @Param("offset") long offset, @Param("limit") int limit, @Param("keyword") String keyword);
}
