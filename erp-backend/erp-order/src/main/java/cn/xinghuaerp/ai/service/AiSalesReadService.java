package cn.xinghuaerp.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import cn.xinghuaerp.open.jd.model.Refund;
import cn.xinghuaerp.open.wei.model.Product;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

/** 销售与订单资料汇总；不读取买家、地址或电话，不执行订单操作。 */
@Service
@RequiredArgsConstructor
public class AiSalesReadService {
    private final JdbcTemplate jdbc;
    private static final String PAID = " AND o.payment>0 AND o.order_status IN (1,2,3,101)";
    public record Metrics(long orders, long paidOrders, BigDecimal payment) {}
    public record Day(String day, long orders, BigDecimal payment) {}
    public record Status(Integer status, long orders) {}
    public record Shop(String shopId, long orders, BigDecimal payment) {}
    public record Product(String skuId, String title, long quantity) {}
    public record Quality(long unlinkedRows, long invalidQuantityRows) {}
    /** 单个 SKU 的表现行：销售为明细聚合口径，退款按退款单登记 SKU 归集；quadrant 为四象限启发式分类。 */
    public record SkuRow(String skuId, String title, long paidOrders, long quantity, BigDecimal salesAmount,
                         long refundOrders, BigDecimal refundAmount, BigDecimal refundRate, String quadrant) {}
    /** SKU 表现排行：分布与集中度始终基于全量行，rows 仅展示前 rowLimit 行（salesAmount 降序）。 */
    public record SkuPerformance(int days, LocalDateTime start, LocalDateTime endExclusive, LocalDateTime retrievedAt,
                                 long skuCount, long unlinkedRefundRows, BigDecimal medianQuantity, BigDecimal medianRefundRate,
                                 Map<String,Integer> topShares, Map<String,Integer> quadrantCounts,
                                 Map<String,BigDecimal> quadrantSales, List<SkuRow> rows, String limitation) {}
    /** 聚合中间行：销售聚合与退款归集合并后的全量数据。 */
    private record Agg(String skuId, String title, long paidOrders, long quantity, BigDecimal salesAmount,
                       long refundOrders, BigDecimal refundAmount) {}
    public record Overview(String scope, int days, LocalDateTime start, LocalDateTime endExclusive,
                           LocalDateTime retrievedAt, Metrics current, Metrics previous,
                           List<Day> trend, List<Status> statuses, List<Shop> shops, List<Product> products,
                           Quality quality, String limitation) {}
    private record Filter(String sql, List<Object> args, String label) {}

    private Filter filter(BusinessSubjectScopeService.Scope scope, LocalDateTime start, LocalDateTime end) {
        if (scope == null) throw new IllegalArgumentException("账号范围无效");
        String sql = " FROM o_order o WHERE o.order_time>=? AND o.order_time<?";
        var args = new ArrayList<Object>(); args.add(start); args.add(end);
        String label;
        switch (scope.type()) {
            case "HEADQUARTERS" -> label = "总部";
            case "MERCHANT" -> {
                if (scope.merchantId()==null || scope.merchantId()<=0) throw new IllegalArgumentException("商户范围无效");
                sql += " AND o.merchant_id=?"; args.add(scope.merchantId()); label="绑定商户";
            }
            case "SHOP" -> {
                if (scope.merchantId()==null || scope.merchantId()<0 || scope.shopId()==null || scope.shopId()<=0)
                    throw new IllegalArgumentException("店铺范围无效");
                sql += " AND o.merchant_id=? AND o.shop_id=?"; args.add(scope.merchantId()); args.add(scope.shopId()); label="绑定店铺";
            }
            default -> throw new IllegalArgumentException("仓库或供应商账号无法按销售订单归属查询");
        }
        return new Filter(sql,args,label);
    }
    private Metrics metrics(Filter f) {
        return jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(CASE WHEN o.payment>0 AND o.order_status IN (1,2,3,101) THEN 1 ELSE 0 END),0),"
                        + "COALESCE(SUM(CASE WHEN o.payment>0 AND o.order_status IN (1,2,3,101) THEN o.payment ELSE 0 END),0)"+f.sql(),
                (rs,n)->new Metrics(rs.getLong(1),rs.getLong(2),rs.getBigDecimal(3)),f.args().toArray());
    }
    @Transactional(readOnly=true)
    public Overview overview(int days, BusinessSubjectScopeService.Scope scope) {
        if (days!=7 && days!=30) throw new IllegalArgumentException("仅支持7或30个完整自然日");
        var end=LocalDate.now().atStartOfDay(); var start=end.minusDays(days);
        var f=filter(scope,start,end); var previous=filter(scope,start.minusDays(days),start);
        var current=metrics(f); var prior=metrics(previous);
        var raw=jdbc.query("SELECT CAST(o.order_time AS DATE) sales_day,COUNT(*),COALESCE(SUM(o.payment),0)"+f.sql()+PAID
                        + " GROUP BY CAST(o.order_time AS DATE) ORDER BY sales_day",
                (rs,n)->new Day(rs.getDate(1).toLocalDate().toString(),rs.getLong(2),rs.getBigDecimal(3)),f.args().toArray());
        var byDay=new java.util.HashMap<String,Day>(); raw.forEach(row->byDay.put(row.day(),row));
        var trend=new ArrayList<Day>();
        for(int i=0;i<days;i++) { var date=start.plusDays(i).toLocalDate().toString(); trend.add(byDay.getOrDefault(date,new Day(date,0,BigDecimal.ZERO))); }
        var statuses=jdbc.query("SELECT o.order_status,COUNT(*)"+f.sql()+" GROUP BY o.order_status ORDER BY o.order_status",
                (rs,n)->new Status(rs.getObject(1,Integer.class),rs.getLong(2)),f.args().toArray());
        var shops=jdbc.query("SELECT o.shop_id,COUNT(*),COALESCE(SUM(o.payment),0)"+f.sql()+PAID
                        + " GROUP BY o.shop_id ORDER BY SUM(o.payment) DESC,o.shop_id LIMIT 20",
                (rs,n)->new Shop(rs.getString(1),rs.getLong(2),rs.getBigDecimal(3)),f.args().toArray());
        String items=f.sql().replace(" FROM o_order o WHERE", " FROM o_order o JOIN o_order_item i ON i.order_id=o.id WHERE")+PAID;
        var products=jdbc.query("SELECT i.goods_sku_id,MAX(i.goods_title),SUM(i.quantity)"+items
                        + " AND i.goods_sku_id>0 AND i.quantity>0 GROUP BY i.goods_sku_id ORDER BY SUM(i.quantity) DESC,i.goods_sku_id LIMIT 20",
                (rs,n)->new Product(rs.getString(1),rs.getString(2),rs.getLong(3)),f.args().toArray());
        var quality=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN i.goods_sku_id IS NULL OR i.goods_sku_id<=0 THEN 1 ELSE 0 END),0),"
                        + "COALESCE(SUM(CASE WHEN i.quantity IS NULL OR i.quantity<=0 THEN 1 ELSE 0 END),0)"+items,
                (rs,n)->new Quality(rs.getLong(1),rs.getLong(2)),f.args().toArray());
        return new Overview(f.label(),days,start,end,LocalDateTime.now(),current,prior,trend,statuses,shops,products,quality,
                "按下单时间统计完整自然日。总订单数及状态分布包含窗口内全部状态；支付金额、每日趋势、店铺与商品仅含payment>0且当前状态为待发货、已发货、已完成或部分发货的订单。金额未扣退款、未做多币种转换，不等于净收入或利润。商品榜仅含已关联内部SKU且数量为正的明细；店铺和商品各展示前20项，不代表全部。上期为紧邻的同等天数。状态为查询时当前值，历史结果可能随状态变化。空结果仅代表当前授权范围及统计窗口。仅提供资料，由人判断和处理。");
    }

    /**
     * SKU 表现排行完整版：按 PAID 订单明细聚合销售，退款按退款单登记 SKU 归集（复用财务侧唯一匹配规则），
     * 在 Java 内存计算销量/退款率中位数与四象限分类、TOP5/10/20 集中度；分布与集中度基于全量行，rows 截断展示。
     */
    @Transactional(readOnly=true)
    public SkuPerformance skuPerformance(int days, BusinessSubjectScopeService.Scope scope, int rowLimit) {
        if (days!=7 && days!=30) throw new IllegalArgumentException("仅支持7或30个完整自然日");
        if (rowLimit<1 || rowLimit>5000) throw new IllegalArgumentException("展示行数仅支持1至5000");
        var end=LocalDate.now().atStartOfDay(); var start=end.minusDays(days);
        var f=filter(scope,start,end);
        // 销售：PAID 订单 JOIN 明细，按内部 SKU 聚合（仅 goods_sku_id>0 且 quantity>0 的明细）。
        String salesFrom=f.sql().replace(" FROM o_order o WHERE"," FROM o_order o JOIN o_order_item i ON i.order_id=o.id WHERE")+PAID;
        var aggs=jdbc.query("SELECT i.goods_sku_id,MAX(i.goods_title),COUNT(DISTINCT o.id),SUM(i.quantity),COALESCE(SUM(CAST(i.payment AS DECIMAL(20,2))),0)"+salesFrom
                        +" AND i.goods_sku_id>0 AND i.quantity>0 GROUP BY i.goods_sku_id",
                (rs,n)->new Agg(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getLong(4),rs.getBigDecimal(5),0L,BigDecimal.ZERO),f.args().toArray());
        // 退款：复用财务侧规则——单号+商户+店铺唯一匹配原单，已完成类型1/10/11退款，申请时间早于窗口结束，按退款单登记 SKU 归集。
        String matches="(SELECT COUNT(*) FROM o_order x WHERE x.order_num=r.order_num AND x.merchant_id=r.merchant_id AND x.shop_id=r.shop_id)";
        String refundFrom=f.sql().replace(" FROM o_order o WHERE"," FROM o_refund r JOIN o_order o ON o.order_num=r.order_num AND o.merchant_id=r.merchant_id AND o.shop_id=r.shop_id WHERE")+PAID
                +" AND r.status IN (4,10010,10020) AND r.refund_type IN (1,10,11) AND r.create_time<? AND "+matches+"=1";
        var refundArgs=new ArrayList<Object>(f.args()); refundArgs.add(end);
        record RefundAgg(long orders, BigDecimal amount) {}
        var refundBySku=new java.util.HashMap<String,RefundAgg>();
        jdbc.query("SELECT r.goods_sku_id,COUNT(DISTINCT r.id),COALESCE(SUM(CASE WHEN r.refund_fee>=0 THEN CAST(r.refund_fee AS DECIMAL(20,2)) ELSE 0 END),0)"+refundFrom
                        +" GROUP BY r.goods_sku_id",
                rs-> { while(rs.next()) refundBySku.put(rs.getString(1),new RefundAgg(rs.getLong(2),rs.getBigDecimal(3))); return null; },
                refundArgs.toArray());
        long unlinked=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN r.goods_sku_id IS NULL OR r.goods_sku_id<=0 THEN 1 ELSE 0 END),0)"+refundFrom,
                Long.class,refundArgs.toArray());
        var merged=new ArrayList<Agg>(aggs.size());
        for (var row:aggs) {
            var refund=refundBySku.get(row.skuId());
            merged.add(new Agg(row.skuId(),row.title(),row.paidOrders(),row.quantity(),row.salesAmount(),
                    refund==null?0L:refund.orders(),refund==null?BigDecimal.ZERO:refund.amount()));
        }
        long skuCount=merged.size();
        // 退款率 = 退款金额/商品销售额（销售额为0时不计算）；中位数偶数取中间两值平均。
        var rateBySku=new java.util.HashMap<String,BigDecimal>();
        for (var row:merged) if (row.salesAmount().signum()>0)
            rateBySku.put(row.skuId(),row.refundAmount().divide(row.salesAmount(),6, RoundingMode.HALF_UP));
        BigDecimal medianQuantity=null; BigDecimal medianRefundRate=null;
        if (skuCount>=2) {
            medianQuantity=median(merged.stream().map(r->BigDecimal.valueOf(r.quantity())).sorted().toList());
            var rates=rateBySku.values().stream().sorted().toList();
            medianRefundRate=rates.isEmpty()?null:median(rates);
        }
        // 集中度：TOP5/10/20 销售额占比，整数表示百分比×10（357=35.7%），始终基于全量行。
        var sorted=merged.stream().sorted(Comparator.comparing(Agg::salesAmount).reversed().thenComparing(Agg::skuId)).toList();
        BigDecimal totalSales=merged.stream().map(Agg::salesAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        Map<String,Integer> topShares=new java.util.LinkedHashMap<>();
        topShares.put("top5",share(sorted,totalSales,5));
        topShares.put("top10",share(sorted,totalSales,10));
        topShares.put("top20",share(sorted,totalSales,20));
        // 四象限：判高用 >=（等于中位数归高侧）；无中位数或退款率不可算时归 null 不分类。
        Map<String,Integer> quadrantCounts=new java.util.LinkedHashMap<>();
        Map<String,BigDecimal> quadrantSales=new java.util.LinkedHashMap<>();
        var allRows=new ArrayList<SkuRow>(merged.size());
        for (var row:sorted) {
            var rate=rateBySku.get(row.skuId());
            String quadrant=null;
            if (medianQuantity!=null && medianRefundRate!=null && rate!=null) {
                boolean highSales=BigDecimal.valueOf(row.quantity()).compareTo(medianQuantity)>=0;
                boolean highRefund=rate.compareTo(medianRefundRate)>=0;
                quadrant=(highSales?"high":"low")+"Sales"+(highRefund?"High":"Low")+"Refund";
                quadrantCounts.merge(quadrant,1,Integer::sum);
                quadrantSales.merge(quadrant,row.salesAmount(),BigDecimal::add);
            }
            allRows.add(new SkuRow(row.skuId(),row.title(),row.paidOrders(),row.quantity(),row.salesAmount(),
                    row.refundOrders(),row.refundAmount(),rate,quadrant));
        }
        var rows=allRows.stream().limit(rowLimit).toList();
        String limitation="销售口径为支付金额>0且状态为待发货、已发货、已完成或部分发货的订单，按下单时间统计完整自然日；商品销量与金额来自订单明细，未扣退款、未换汇。"
                +"退款按单号+商户+店铺唯一匹配原单后，归集到退款单登记的内部SKU，仅含已完成（本地已办结4、平台退款完成10010、售后成功10020）的类型1/10/11退款且申请时间早于窗口结束；"
                +"未关联或SKU无效的退款单不参与分摊，另计异常数；退款归集仅覆盖窗口内有销售记录的SKU，无销售记录的SKU退款不计入。退款订单数为该SKU关联的退款单笔数。退款率=退款金额/商品销售额，分母为明细金额而非订单支付口径，销售额为0时不计算。"
                +"集中度TOP5/10/20为窗口内销售额占比（整数表示百分比×10，如357即35.7%），仅代表窗口内结构。四象限按销量与退款率中位数启发式划分（等于中位数归高侧），非平台标准分类；"
                +"历史结果随订单与退款状态变化。商品较多时仅展示销售额前"+rowLimit+"行，分布与集中度仍基于全量行计算。"
                +(skuCount<2?"商品数量不足，未做四象限分类。":"")+"仅提供资料，由人判断和处理。";
        return new SkuPerformance(days,start,end,LocalDateTime.now(),skuCount,unlinked,medianQuantity,medianRefundRate,
                topShares,quadrantCounts,quadrantSales,rows,limitation);
    }

    /** 中位数：偶数取中间两值平均。 */
    private BigDecimal median(List<BigDecimal> sorted) {
        int size=sorted.size();
        return size%2==1 ? sorted.get(size/2) : sorted.get(size/2-1).add(sorted.get(size/2)).divide(BigDecimal.valueOf(2));
    }

    /** TOP N 销售额占比：整数表示百分比×10；全量销售额为 0 时返回 null。 */
    private Integer share(List<Agg> sorted, BigDecimal total, int limit) {
        if (total.signum()==0) return null;
        BigDecimal sum=BigDecimal.ZERO;
        for (int i=0;i<Math.min(limit,sorted.size());i++) sum=sum.add(sorted.get(i).salesAmount());
        return sum.multiply(BigDecimal.valueOf(1000)).divide(total,0,RoundingMode.HALF_UP).intValue();
    }
}
