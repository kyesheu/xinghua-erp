package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.ai.mapper.AiInventoryMapper;
import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.ai.vo.AiInventoryOverviewRow;
import cn.xinghuaerp.ai.vo.AiInventoryEmptyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import cn.xinghuaerp.ai.vo.AiInventoryBatchAudit;
import cn.xinghuaerp.ai.vo.AiPurchaseLedgerRow;
import cn.xinghuaerp.ai.vo.AiPurchaseShipment;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

@Service
@RequiredArgsConstructor
public class AiInventoryReadService {
    private final AiInventoryMapper mapper;
    public record SkuChoice(String skuId, String skuCode, String goodsName, String skuName) {}
    public record Sales(long skuId, long quantity, LocalDateTime start, LocalDateTime endExclusive, String scope, String limitation) {}
    public record PurchaseInbound(List<Map<String,Object>> records, List<Map<String,Object>> withoutTask, String scope, String limitation,
                                  List<cn.xinghuaerp.ai.vo.AiPurchaseLedgerRow> ledger,
                                  List<cn.xinghuaerp.ai.vo.AiPurchaseShipment> shipments) {
        public PurchaseInbound(List<Map<String,Object>> records, List<Map<String,Object>> withoutTask, String scope, String limitation) {
            this(records, withoutTask, scope, limitation, List.of(), List.of());
        }
    }
    public record DataScope(Long merchantId, Long shopId, String label) {}
    public record Snapshot(List<OGoodsInventory> records, String scope, String limitation) {}
    public record AnalysisSnapshot(LocalDateTime retrievedAt, Snapshot inventory, Sales sales, PurchaseInbound purchase,
                                   List<cn.xinghuaerp.ai.vo.AiInventoryBatchAudit> batchAudit) {}
    /** subjectType 为请求账号的主体类型（HEADQUARTERS/MERCHANT/SHOP/...），
     *  供前端隐藏总部专属的采购卡片，避免非总部账号看到可点击却必然 403 的操作。 */
    public record Overview(long total, int page, int size, String scope, String limitation, boolean emptyScopesTruncated, List<AiInventoryOverviewRow> records, String subjectType) {}
    public record RiskSummary(long total, String scope, String limitation, Map<String,Long> risks) {}
    public record Dossier(LocalDateTime retrievedAt, Snapshot inventory, Sales sales7Days, Sales sales30Days,
                          PurchaseInbound purchase, boolean salesVisible, boolean purchaseVisible,
                          List<cn.xinghuaerp.ai.vo.AiInventoryBatchAudit> batchAudit) {}

    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public AnalysisSnapshot analysisSnapshot(Long skuId, Long warehouseId, int days, BusinessSubjectScopeService.Scope scope,
                                             boolean includeSales, boolean includePurchases) {
        var stock = find(skuId, warehouseId, scope);
        boolean businessVisible = !"WAREHOUSE".equals(scope.type());
        var salesData = businessVisible && includeSales ? sales(skuId, days, scope) : null;
        var purchaseData = businessVisible && includePurchases ? purchaseInbound(skuId, warehouseId, scope) : null;
        return new AnalysisSnapshot(LocalDateTime.now(), stock, salesData, purchaseData, batchAudit(stock));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Dossier dossier(Long skuId, BusinessSubjectScopeService.Scope scope, boolean includeSales, boolean includePurchases) {
        var stock = find(skuId, null, scope);
        boolean salesVisible = includeSales && !"WAREHOUSE".equals(scope.type());
        boolean purchaseVisible = includePurchases && !"WAREHOUSE".equals(scope.type());
        return new Dossier(LocalDateTime.now(), stock,
                salesVisible ? sales(skuId, 7, scope) : null,
                salesVisible ? sales(skuId, 30, scope) : null,
                purchaseVisible ? purchaseInbound(skuId, null, scope) : null, salesVisible, purchaseVisible, batchAudit(stock));
    }

    private List<cn.xinghuaerp.ai.vo.AiInventoryBatchAudit> batchAudit(Snapshot stock) {
        var ids = stock.records().stream().map(OGoodsInventory::getId).toList();
        return ids.isEmpty() ? List.of() : mapper.batchAudit(ids);
    }

    public Overview overview(int page, int size, BusinessSubjectScopeService.Scope scope) {
        return overview(page, size, scope, false, false);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public RiskSummary riskSummary(BusinessSubjectScopeService.Scope scope) {
        var first = overviewInternal(1,100,scope,false,false,null,false,true);
        var counts = new java.util.LinkedHashMap<String,Long>();
        for (var row : first.records()) counts.merge(row.getRisk(),1L,Long::sum);
        return new RiskSummary(first.total(), first.scope(), first.limitation(), counts);
    }

    public Overview overview(int page, int size, BusinessSubjectScopeService.Scope scope, boolean includeSales) {
        return overview(page, size, scope, includeSales, false);
    }

    public Overview overview(int page, int size, BusinessSubjectScopeService.Scope scope, boolean includeSales, boolean includePurchases) {
        return overviewInternal(page, size, scope, includeSales, includePurchases, null, false);
    }
    public Overview productOverview(int page, int size, BusinessSubjectScopeService.Scope scope, String keyword) {
        if (keyword != null && keyword.trim().length() > 100) throw new IllegalArgumentException("搜索词不能超过100字");
        return overviewInternal(page, size, scope, false, false, keyword == null ? null : keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_"), true);
    }
    private Overview overviewInternal(int page, int size, BusinessSubjectScopeService.Scope scope, boolean includeSales, boolean includePurchases, String keyword, boolean product) {
        return overviewInternal(page,size,scope,includeSales,includePurchases,keyword,product,false);
    }
    private Overview overviewInternal(int page, int size, BusinessSubjectScopeService.Scope scope, boolean includeSales, boolean includePurchases, String keyword, boolean product, boolean all) {
        if (scope == null || page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("分页或账号范围无效");
        Long warehouseId = null; DataScope dataScope;
        if ("WAREHOUSE".equals(scope.type())) {
            if (scope.subjectId() == null || scope.subjectId() <= 0) throw new IllegalArgumentException("仓库范围无效");
            warehouseId = scope.subjectId(); dataScope = new DataScope(null, null, "绑定仓库");
        } else dataScope = resolve(scope);
        long offset = ((long) page - 1) * size;
        long total = product ? mapper.countProductOverview(dataScope.merchantId(), dataScope.shopId(), warehouseId, keyword) : mapper.countOverview(dataScope.merchantId(), dataScope.shopId(), warehouseId);
        var records = all ? mapper.allOverview(dataScope.merchantId(),dataScope.shopId(),warehouseId) : product ? mapper.productOverview(dataScope.merchantId(), dataScope.shopId(), warehouseId, offset, size, keyword) : mapper.overview(dataScope.merchantId(), dataScope.shopId(), warehouseId, offset, size);
        if (all && records.size() > 20000) throw new IllegalArgumentException("库存风险统计超过20000个SKU，请缩小业务范围");
        if (includeSales && warehouseId == null && !records.isEmpty()) {
            var ids = records.stream().map(row -> Long.valueOf(row.getSkuId())).toList();
            var end = LocalDate.now().atStartOfDay();
            var sales = mapper.overviewSales(ids, end.minusDays(30), end, dataScope.merchantId(), dataScope.shopId());
            var quantities = new java.util.HashMap<String, Long>();
            for (var item : sales) quantities.put(item.getSkuId(), item.getSales30Days());
            for (var row : records) row.setSales30Days(quantities.getOrDefault(row.getSkuId(), 0L));
        }
        if (includePurchases && warehouseId == null && !records.isEmpty()) {
            var ids = records.stream().map(row -> Long.valueOf(row.getSkuId())).toList();
            var purchases = mapper.overviewPurchases(ids, dataScope.merchantId(), dataScope.shopId());
            var counts = new java.util.HashMap<String, Long>();
            for (var item : purchases) counts.put(item.getSkuId(), item.getOpenPurchaseOrders());
            for (var row : records) row.setOpenPurchaseOrders(counts.getOrDefault(row.getSkuId(), 0L));
        }
        for (var row : records) {
            if (row.getInventoryRows() == 0 || row.getGoodRows() == 0) row.setRisk("待核实库存");
            else if (row.getInvalidRows() > 0 || row.getGoodRows() > row.getDistinctGoodScopes()) row.setRisk("库存数据异常");
            else if (Boolean.TRUE.equals(row.getBatchIssue())) row.setRisk("批次待核实");
            else if (row.getAvailableQuantity() <= 0) row.setRisk("缺货");
            else if (Boolean.TRUE.equals(row.getEmptyInventoryScope())) row.setRisk("部分范围缺货");
            else if (row.getDistinctWarehouses() > 1) row.setRisk("多仓待核实");
            else if (row.getLowQty() == null || row.getLowQty() <= 0) row.setRisk("未设置预警线");
            else if (row.getAvailableQuantity() <= row.getLowQty()) row.setRisk("低库存");
            else row.setRisk("正常");
        }
        var emptyIds = records.stream().filter(r -> !all && ("缺货".equals(r.getRisk()) || "部分范围缺货".equals(r.getRisk())))
                .map(r -> Long.valueOf(r.getSkuId())).toList();
        var emptyScopes = emptyIds.isEmpty() ? List.<AiInventoryEmptyScope>of() : mapper.emptyScopes(emptyIds, dataScope.merchantId(), dataScope.shopId(), warehouseId);
        boolean truncated = emptyScopes.size() > 1000;
        var bySku = new java.util.HashMap<String, java.util.List<AiInventoryEmptyScope>>();
        for (var item : emptyScopes.subList(0, Math.min(1000, emptyScopes.size())))
            bySku.computeIfAbsent(item.getSkuId(), key -> new java.util.ArrayList<>()).add(item);
        for (var row : records) row.setEmptyScopes(bySku.getOrDefault(row.getSkuId(), List.of()));
        return new Overview(total, page, size, dataScope.label(),
                "按授权范围汇总良品库存，预警线来自商品 SKU；部分范围缺货表示某仓库及主体组合的可用良品为零，多仓商品仍须逐仓核实。批次缺失、数量或归属异常标为待核实；孤立批次及来源仍需人工核对。无库存记录不等于零库存。仓库账号只能看到已有该仓库存记录的 SKU，未建记录的商品无法按仓归属。近30日销量未扣退款、未关联 SKU 的订单不计入，且不能按仓归属；未完成采购单数不代表在途数量。以上仅供参考，不用于自动补货。", truncated, records, scope.type());
    }

    public List<SkuChoice> search(String keyword, BusinessSubjectScopeService.Scope scope) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() > 100 || scope == null)
            throw new IllegalArgumentException("请输入不超过100字的商品名称或SKU编码");
        DataScope dataScope = resolve(scope);
        return mapper.search(keyword.trim(), dataScope.merchantId(), dataScope.shopId()).stream()
                .map(row -> new SkuChoice(String.valueOf(row.getSkuId()), row.getSkuCode(), row.getGoodsName(), row.getSkuName())).toList();
    }

    public Snapshot find(Long skuId, Long warehouseId, BusinessSubjectScopeService.Scope scope) {
        if (skuId == null || skuId <= 0 || (warehouseId != null && warehouseId <= 0) || scope == null)
            throw new IllegalArgumentException("SKU 或仓库参数无效");
        DataScope dataScope;
        if ("WAREHOUSE".equals(scope.type())) {
            if (scope.subjectId() == null || scope.subjectId() <= 0 || (warehouseId != null && !warehouseId.equals(scope.subjectId())))
                throw new IllegalArgumentException("无该仓库访问范围");
            warehouseId = scope.subjectId(); dataScope = new DataScope(null, null, "绑定仓库");
        } else dataScope = resolve(scope);
        return new Snapshot(mapper.find(skuId, warehouseId, dataScope.merchantId(), dataScope.shopId()), dataScope.label(),
                "库存最多返回200条记录，空结果不代表库存为零；销量及采购信息按独立口径查询，逐仓需求、到货时间仍须人工核实，不具备自动补货依据。");
    }

    public Sales sales(Long skuId, int days, BusinessSubjectScopeService.Scope scope) {
        if (skuId == null || skuId <= 0 || (days != 7 && days != 30) || scope == null)
            throw new IllegalArgumentException("SKU 或统计周期无效，仅支持7天或30天");
        DataScope dataScope = resolve(scope);
        LocalDateTime end = LocalDate.now().atStartOfDay();
        LocalDateTime start = end.minusDays(days);
        long quantity = mapper.salesQuantity(skuId, start, end, dataScope.merchantId(), dataScope.shopId());
        return new Sales(skuId, quantity, start, end, dataScope.label(),
                "仅统计已付款且当前状态为待发货、已发货、已完成或部分发货的订单商品数量；未扣退款，未关联内部SKU的明细未计入；包含所有履约方式，不可按仓库归属，不能直接用于补货。");
    }

    public PurchaseInbound purchaseInbound(Long skuId, Long warehouseId, BusinessSubjectScopeService.Scope scope) {
        if (skuId == null || skuId <= 0 || (warehouseId != null && warehouseId <= 0) || scope == null)
            throw new IllegalArgumentException("SKU 或仓库参数无效");
        DataScope dataScope = resolve(scope);
        var ledger = mapper.purchaseLedger(skuId, dataScope.merchantId(), dataScope.shopId());
        for (var row : ledger) {
            if (row.getOrderedQuantity() == null || row.getOrderedQuantity() <= 0 || row.getInvalidTasks() > 0
                    || row.getTaskCount() > 1 || row.getEntryCount() > 1
                    || (row.getTaskCount() > 0 && !row.getTaskQuantity().equals(row.getOrderedQuantity())))
                row.setCheckStatus("数量或任务异常，待核实");
            else if (row.getTaskCount() == 0 && (row.getEntryCount() > 0 || Integer.valueOf(3).equals(row.getOrderStatus())))
                row.setCheckStatus("已有入库单但明细未匹配，待核实");
            else if (row.getWarehouseId() == null)
                row.setCheckStatus("未确认仓库，待核实");
            else {
                row.setRemainingQuantity(row.getOrderedQuantity() - row.getReceivedQuantity());
                row.setCheckStatus(row.getRemainingQuantity() == 0 ? "已登记全部入库" : row.getTaskCount() == 0 ? "未生成任务，未确认到货" : "待入库，未确认到货");
            }
        }
        return new PurchaseInbound(mapper.purchaseInbound(skuId, warehouseId, dataScope.merchantId(), dataScope.shopId()),
                mapper.purchaseWithoutTask(skuId, dataScope.merchantId(), dataScope.shopId()), dataScope.label(),
                "入库任务、未匹配任务采购和采购核对台账分别最多200条；后两者按授权主体查询所有仓库。台账按采购明细核对全部关联任务的实入数量，重复、缺失或数量不一致时不输出剩余量。历史关联错误仍需人工核实；未入库量不等于已发货或有效在途，不能自动补货。物流资料为整单登记，不证明本SKU已发货数量或预计到货日，最多200条。", ledger,
                mapper.purchaseShipments(skuId, warehouseId, dataScope.merchantId(), dataScope.shopId()));
    }

    private DataScope resolve(BusinessSubjectScopeService.Scope scope) {
        Long merchantId;
        Long shopId;
        String label;
        switch (scope.type()) {
            case "HEADQUARTERS" -> { merchantId = null; shopId = null; label = "总部"; }
            case "MERCHANT" -> {
                if (scope.merchantId() == null || scope.merchantId() <= 0) throw new IllegalArgumentException("商户范围无效");
                merchantId = scope.merchantId(); shopId = null; label = "商户";
            }
            case "SHOP" -> {
                if (scope.merchantId() == null || scope.merchantId() < 0 || scope.shopId() == null || scope.shopId() <= 0)
                    throw new IllegalArgumentException("店铺范围无效");
                merchantId = scope.merchantId(); shopId = scope.shopId(); label = "店铺独立库存";
            }
            default -> throw new IllegalArgumentException("当前账号不支持此库存查询");
        }
        return new DataScope(merchantId, shopId, label);
    }
}
