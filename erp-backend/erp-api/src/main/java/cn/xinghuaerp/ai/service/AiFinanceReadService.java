package cn.xinghuaerp.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import cn.xinghuaerp.system.service.BusinessSubjectScopeService;
/** 财务资料汇总；订单退款差额与财务流水分别展示，利润来自已审核结算单口径。 */
@Service
@RequiredArgsConstructor
public class AiFinanceReadService {
    /**
     * 退款到账的售后状态：本地已办结 4 + 平台退款完成 10010 + 平台售后成功 10020。
     *
     * <p>{@code o_refund.status} 混用两套词表（本地 {@code LocalAfterSaleService} 写 0 待处理/1 已关闭/4 已办结，
     * 平台同步记录写 10001–10094），只写 4 会漏掉全部平台退款，使「支付减退款」差额偏高。</p>
     *
     * <p><b>刻意不含</b> 10091 换货成功、10094 维修成功、10093 维修关闭——这些不是退款到账。</p>
     */
    private static final String REFUNDED_STATUS = "4,10010,10020";
    private final JdbcTemplate jdbc;
    public record Orders(long count, BigDecimal payment, long negativePaymentRows) {}
    public record Refunds(long count, BigDecimal amount, long invalidRows, long unmatchedRows, long ambiguousRows) {}
    public record Account(String accountType, long entries, BigDecimal income, BigDecimal expense) {}
    public record Ledger(boolean available, long entries, BigDecimal income, BigDecimal expense,
                         long invalidRows, List<Account> accounts, String limitation) {}
    /**
     * 结算利润聚合（已审核结算单口径）。
     *
     * <p>窗口与主体范围按关联原单（{@code o_order.order_time} 与实时主体）判定，
     * 与订单、退款资料同口径；金额为结算单原始记录，不重复计算。</p>
     */
    public record Settlement(boolean available, long settledOrders, BigDecimal salesAmount,
                             BigDecimal purchaseCost, BigDecimal shippingFee, BigDecimal platformFee,
                             BigDecimal marketingFee, BigDecimal otherFee, BigDecimal totalCost,
                             BigDecimal profit, long zeroCostSettled, long unsettledPaidOrders) {}
    public record Overview(String scope, int days, LocalDateTime start, LocalDateTime endExclusive,
                           LocalDateTime retrievedAt, Orders orders, Refunds refunds, BigDecimal paymentLessRefund,
                           BigDecimal profit, Settlement settlement, Ledger ledger, List<String> missingInputs, String limitation) {}
    /** 单笔负利润订单：金额直接取已审核结算单记录，causes 为确定性诱因（可多命中并列）。 */
    public record LossOrder(String orderNo, BigDecimal salesAmount, BigDecimal purchaseCost, BigDecimal shippingFee,
                            BigDecimal platformFee, BigDecimal marketingFee, BigDecimal otherFee, BigDecimal totalCost,
                            BigDecimal profit, List<String> causes) {}
    /** 订单级亏损穿透：最多 5001 行探测截断至 5000，causeCounts 与明细一致；结算表缺失时 available=false。 */
    public record LossOrders(boolean available, int days, LocalDateTime start, LocalDateTime endExclusive,
                             LocalDateTime retrievedAt, long count, boolean truncated, List<LossOrder> orders,
                             Map<String,Long> causeCounts, String limitation) {}
    /** 单店铺对比行：订单/支付为财务 cohort 口径，结算列为已审核结算单聚合，未结算店铺利润为 null。 */
    public record ShopComparison(String shopId, long orders, BigDecimal payment, BigDecimal refundAmount,
                                 long settledOrders, BigDecimal settledSalesAmount, BigDecimal profit) {}
    public record ShopComparisonResult(int days, LocalDateTime start, LocalDateTime endExclusive,
                                       LocalDateTime retrievedAt, List<ShopComparison> shops, String limitation) {}
    private record Bound(String sql, List<Object> args, String label) {}
    private Bound bound(BusinessSubjectScopeService.Scope scope,String alias) {
        if(scope==null) throw new IllegalArgumentException("账号范围无效");
        var args=new ArrayList<Object>();String sql="";String label;
        switch(scope.type()) {
            case "HEADQUARTERS" -> label="总部";
            case "MERCHANT" -> {
                if(scope.merchantId()==null || scope.merchantId()<=0) throw new IllegalArgumentException("商户范围无效");
                sql=" AND "+alias+".merchant_id=?";args.add(scope.merchantId());label="绑定商户";
            }
            case "SHOP" -> {
                if(scope.merchantId()==null || scope.merchantId()<0 || scope.shopId()==null || scope.shopId()<=0) throw new IllegalArgumentException("店铺范围无效");
                sql=" AND "+alias+".merchant_id=? AND "+alias+".shop_id=?";args.add(scope.merchantId());args.add(scope.shopId());label="绑定店铺";
            }
            default -> throw new IllegalArgumentException("仓库或供应商账号不支持财务订单归属查询");
        }
        return new Bound(sql,args,label);
    }
    private Object[] args(LocalDateTime start,LocalDateTime end,List<Object> scope) {
        var values=new ArrayList<Object>();values.add(start);values.add(end);values.addAll(scope);return values.toArray();
    }
    private boolean ledgerExists() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            for(String name:List.of("fms_finance_ledger","FMS_FINANCE_LEDGER")) {
                try(var tables=connection.getMetaData().getTables(connection.getCatalog(),null,name,new String[]{"TABLE"})) {
                    while(tables.next()) if("fms_finance_ledger".equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
                }
            }
            return false;
        }));
    }
    private Ledger ledger(LocalDateTime start,LocalDateTime end,Bound scope) {
        if(!ledgerExists()) return new Ledger(false,0,null,null,0,List.of(),"财务流水表尚未建立，未查询到流水不代表零费用；不会自动建表或补记账。");
        String valid="l.income_amount IS NOT NULL AND l.expense_amount IS NOT NULL AND l.income_amount>=0 AND l.expense_amount>=0";
        String from=" FROM fms_finance_ledger l WHERE l.expense_date>=? AND l.expense_date<?"+scope.sql();
        var values=args(start,end,scope.args());
        var totals=jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(CASE WHEN "+valid+" THEN CAST(l.income_amount AS DECIMAL(20,2)) ELSE 0 END),0),"
                        +"COALESCE(SUM(CASE WHEN "+valid+" THEN CAST(l.expense_amount AS DECIMAL(20,2)) ELSE 0 END),0),COALESCE(SUM(CASE WHEN "+valid+" THEN 0 ELSE 1 END),0)"+from,
                (rs,n)->new Ledger(true,rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getLong(4),List.of(),""),values);
        var accounts=jdbc.query("SELECT l.account_type,COUNT(*),COALESCE(SUM(CAST(l.income_amount AS DECIMAL(20,2))),0),COALESCE(SUM(CAST(l.expense_amount AS DECIMAL(20,2))),0)"+from
                        +" AND "+valid+" GROUP BY l.account_type ORDER BY l.account_type LIMIT 20",
                (rs,n)->new Account(rs.getString(1),rs.getLong(2),rs.getBigDecimal(3),rs.getBigDecimal(4)),values);
        return new Ledger(true,totals.entries(),totals.income(),totals.expense(),totals.invalidRows(),accounts,
                "仅按流水expense_date统计已登记资料；缺失或负金额行不计金额，另列异常数。科目前20项，不代表全部。不核验凭证真实性、费用完整性或重复记账；不与订单支付、退款相加计算利润，可能重复记录相同业务。");
    }
    private boolean settlementTableExists() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            for(String name:List.of("fms_order_settlement","FMS_ORDER_SETTLEMENT")) {
                try(var tables=connection.getMetaData().getTables(connection.getCatalog(),null,name,new String[]{"TABLE"})) {
                    while(tables.next()) if("fms_order_settlement".equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
                }
            }
            return false;
        }));
    }
    /** 已审核结算单聚合：按关联原单窗口与主体范围过滤，金额直接取结算单记录，不在 AI 层重算成本。 */
    private Settlement settlement(LocalDateTime start,LocalDateTime end,Bound orderScope) {
        if(!settlementTableExists()) return new Settlement(false,0,null,null,null,null,null,null,null,null,0,0);
        String cohort="o.order_time>=? AND o.order_time<? AND o.payment>0 AND o.order_status IN (1,2,3,11,12,13,22,31,101)";
        String from=" FROM fms_order_settlement s JOIN o_order o ON o.id=s.order_id WHERE s.status=1 AND "+cohort+orderScope.sql();
        var values=args(start,end,orderScope.args());
        var totals=jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(CAST(s.sales_amount AS DECIMAL(20,2))),0),COALESCE(SUM(CAST(s.purchase_cost AS DECIMAL(20,2))),0),"
                        +"COALESCE(SUM(CAST(s.shipping_fee AS DECIMAL(20,2))),0),COALESCE(SUM(CAST(s.platform_fee AS DECIMAL(20,2))),0),COALESCE(SUM(CAST(s.marketing_fee AS DECIMAL(20,2))),0),"
                        +"COALESCE(SUM(CAST(s.other_fee AS DECIMAL(20,2))),0),COALESCE(SUM(CAST(s.total_cost AS DECIMAL(20,2))),0),COALESCE(SUM(CAST(s.profit AS DECIMAL(20,2))),0),"
                        +"COALESCE(SUM(CASE WHEN CAST(s.total_cost AS DECIMAL(20,2))=0 THEN 1 ELSE 0 END),0)"+from,
                (rs,n)->new Settlement(true,rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getBigDecimal(5),
                        rs.getBigDecimal(6),rs.getBigDecimal(7),rs.getBigDecimal(8),rs.getBigDecimal(9),rs.getLong(10),0),values);
        long unsettled=jdbc.queryForObject("SELECT COUNT(*) FROM o_order o WHERE "+cohort+orderScope.sql()
                        +" AND NOT EXISTS (SELECT 1 FROM fms_order_settlement x WHERE x.status=1 AND x.order_id=o.id)",
                Long.class,values);
        return new Settlement(true,totals.settledOrders(),totals.salesAmount(),totals.purchaseCost(),totals.shippingFee(),totals.platformFee(),
                totals.marketingFee(),totals.otherFee(),totals.totalCost(),totals.profit(),totals.zeroCostSettled(),unsettled);
    }
    @Transactional(readOnly=true)
    public Overview overview(int days,BusinessSubjectScopeService.Scope scope) {
        if(days!=7 && days!=30) throw new IllegalArgumentException("仅支持7或30个完整自然日");
        var orderScope=bound(scope,"o");var refundScope=bound(scope,"r");var ledgerScope=bound(scope,"l");
        var end=LocalDate.now().atStartOfDay();var start=end.minusDays(days);
        String cohort=" FROM o_order o WHERE o.order_time>=? AND o.order_time<?"+orderScope.sql();
        String paid=" AND o.payment>0 AND o.order_status IN (1,2,3,11,12,13,22,31,101)";
        var orders=jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(CAST(o.payment AS DECIMAL(20,2))),0),0"+cohort+paid,
                (rs,n)->new Orders(rs.getLong(1),rs.getBigDecimal(2),0),args(start,end,orderScope.args()));
        long negative=jdbc.queryForObject("SELECT COUNT(*)"+cohort+" AND o.payment<0",Long.class,args(start,end,orderScope.args()));
        orders=new Orders(orders.count(),orders.payment(),negative);
        // 相同单号必须同时匹配商户和店铺，且仅匹配一张原单，避免一对多放大退款。
        String match="o.order_num=r.order_num AND o.merchant_id=r.merchant_id AND o.shop_id=r.shop_id";
        String matches="(SELECT COUNT(*) FROM o_order x WHERE x.order_num=r.order_num AND x.merchant_id=r.merchant_id AND x.shop_id=r.shop_id)";
        String refundFrom=" FROM o_refund r JOIN o_order o ON "+match+" WHERE o.order_time>=? AND o.order_time<?"+orderScope.sql()+paid
                +" AND r.status IN ("+REFUNDED_STATUS+") AND r.refund_type IN (1,10,11) AND r.create_time<? AND "+matches+"=1";
        var refundArgs=new ArrayList<Object>(List.of(start,end));refundArgs.addAll(orderScope.args());refundArgs.add(end);
        var matched=jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(CASE WHEN r.refund_fee>=0 THEN CAST(r.refund_fee AS DECIMAL(20,2)) ELSE 0 END),0),"
                        +"COALESCE(SUM(CASE WHEN r.refund_fee IS NULL OR r.refund_fee<0 THEN 1 ELSE 0 END),0)"+refundFrom,
                (rs,n)->new Refunds(rs.getLong(1),rs.getBigDecimal(2),rs.getLong(3),0,0),refundArgs.toArray());
        // 未匹配和歧义项按退款申请窗口统计，与原单窗口退款金额是两种口径。
        var unmatched=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN "+matches+"=0 THEN 1 ELSE 0 END),0),"
                        +"COALESCE(SUM(CASE WHEN "+matches+">1 THEN 1 ELSE 0 END),0) FROM o_refund r WHERE r.create_time>=? AND r.create_time<?"
                        +refundScope.sql()+" AND r.status IN ("+REFUNDED_STATUS+") AND r.refund_type IN (1,10,11)",
                (rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},args(start,end,refundScope.args()));
        var refunds=new Refunds(matched.count(),matched.amount(),matched.invalidRows(),unmatched[0],unmatched[1]);
        var settlement=settlement(start,end,orderScope);
        // 利润只在存在已审核结算单时输出；结算表缺失或窗口内无结算单时为 null，不代表零利润。
        BigDecimal profit=settlement.available() && settlement.settledOrders()>0 ? settlement.profit() : null;
        var missingInputs=new ArrayList<String>();
        if(!settlement.available()) {
            missingInputs.add("历史订单出库时成本快照与成本归属尚未完整验证；当前SKU采购价不能替代历史成本");
            missingInputs.add("平台佣金、支付手续费、物流、广告及税费的完整性未验证");
            missingInputs.add("币种、汇率及财务结算口径未统一");
        } else {
            missingInputs.add("结算成本来自出库批次登记（总成本/单位成本/采购价），无批次记录的订单结算成本为零，需人工核实（当前 "+settlement.zeroCostSettled()+" 笔）");
            missingInputs.add("费用项仅含订单已登记并审核通过的费用单（平台扣点/营销费等），未登记费用不代表为零");
            missingInputs.add("金额为订单原始币种（演示库为人民币元）直接汇总，未启用多币种折算；退款未纳入结算利润");
        }
        return new Overview(orderScope.label(),days,start,end,LocalDateTime.now(),orders,refunds,orders.payment().subtract(refunds.amount()),profit,
                settlement,ledger(start,end,ledgerScope),missingInputs,
                "原单按下单时间取完整自然日，支付包含payment>0且状态为1、2、3、11、12、13、22、31、101的订单，含售后及取消等状态，与销售助手的有效销售口径不同。退款取上述原单唯一关联、申请早于窗口结束且已完成（本地已办结(4)与平台退款完成(10010)/售后成功(10020)）的类型1/10/11退款；完成时间没有可靠专用字段，因此不是期内退款现金流或历史截止快照。支付减退款仅为资料差额，未扣成本费用、未换汇，不能作为毛利、净利或结算收入。异常退款单独提示，超额退款可能使差额为负，不自动归零。未匹配/歧义退款数按退款申请窗口统计。财务流水按记账日期独立展示，不重复相加。结算利润来自已审核结算单（订单实际金额减采购成本、运费及登记费用），不含退款调整，与支付减退款口径不同不可相加；未结算订单不含在内。由人核对和作最终判断。");
    }

    /** 确定性诱因判定：占比阈值判高（严格大于30%），可多命中并列；销售额为0时跳过占比判断。 */
    private List<String> causesOf(BigDecimal sales, BigDecimal purchase, BigDecimal shipping,
                                  BigDecimal platform, BigDecimal marketing, BigDecimal other) {
        var list=new ArrayList<String>();
        if (sales.compareTo(BigDecimal.ZERO)>0) {
            var fees=nz(platform).add(nz(marketing)).add(nz(other));
            if (fees.multiply(BigDecimal.valueOf(100)).compareTo(sales.multiply(BigDecimal.valueOf(30)))>0) list.add("费用占比过高");
            if (nz(shipping).multiply(BigDecimal.valueOf(100)).compareTo(sales.multiply(BigDecimal.valueOf(30)))>0) list.add("运费占比过高");
        }
        if (nz(purchase).compareTo(BigDecimal.ZERO)==0 && sales.compareTo(BigDecimal.ZERO)>0) list.add("零成本待核实");
        return list;
    }
    private BigDecimal nz(BigDecimal value) { return value==null?BigDecimal.ZERO:value; }

    /**
     * 订单级亏损穿透：复用 settlement() 的 cohort（已审核结算单 JOIN 原单，原单窗口+支付口径+主体范围），
     * 取利润<0 的订单按利润升序，LIMIT 5001 探测截断至 5000；诱因在 Java 侧判定，causeCounts 与明细一致。
     */
    @Transactional(readOnly=true)
    public LossOrders lossOrders(int days,BusinessSubjectScopeService.Scope scope) {
        if(days!=7 && days!=30) throw new IllegalArgumentException("仅支持7或30个完整自然日");
        var orderScope=bound(scope,"o");
        var end=LocalDate.now().atStartOfDay();var start=end.minusDays(days);
        if(!settlementTableExists()) return new LossOrders(false,days,start,end,LocalDateTime.now(),0,false,List.of(),Map.of(),
                "结算单表尚未建立，无法识别负利润订单；不代表没有亏损订单，也不会自动建表或补结算。");
        String cohort="o.order_time>=? AND o.order_time<? AND o.payment>0 AND o.order_status IN (1,2,3,11,12,13,22,31,101)";
        String from=" FROM fms_order_settlement s JOIN o_order o ON o.id=s.order_id WHERE s.status=1 AND "+cohort+orderScope.sql()
                +" AND CAST(s.profit AS DECIMAL(20,2))<0";
        var values=args(start,end,orderScope.args());
        var rows=jdbc.query("SELECT o.order_num,COALESCE(CAST(s.sales_amount AS DECIMAL(20,2)),0),COALESCE(CAST(s.purchase_cost AS DECIMAL(20,2)),0),"
                        +"COALESCE(CAST(s.shipping_fee AS DECIMAL(20,2)),0),COALESCE(CAST(s.platform_fee AS DECIMAL(20,2)),0),"
                        +"COALESCE(CAST(s.marketing_fee AS DECIMAL(20,2)),0),COALESCE(CAST(s.other_fee AS DECIMAL(20,2)),0),"
                        +"COALESCE(CAST(s.total_cost AS DECIMAL(20,2)),0),COALESCE(CAST(s.profit AS DECIMAL(20,2)),0)"+from
                        +" ORDER BY CAST(s.profit AS DECIMAL(20,2)) ASC LIMIT 5001",
                (rs,n)->{var sales=rs.getBigDecimal(2);return new LossOrder(rs.getString(1),sales,rs.getBigDecimal(3),rs.getBigDecimal(4),
                        rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getBigDecimal(7),rs.getBigDecimal(8),rs.getBigDecimal(9),
                        causesOf(sales,rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getBigDecimal(7)));},values);
        boolean truncated=rows.size()>5000;
        if(truncated) rows=rows.subList(0,5000);
        var causeCounts=new java.util.LinkedHashMap<String,Long>();
        rows.forEach(order->order.causes().forEach(cause->causeCounts.merge(cause,1L,Long::sum)));
        return new LossOrders(true,days,start,end,LocalDateTime.now(),rows.size(),truncated,List.copyOf(rows),causeCounts,
                "负利润订单按已审核结算单利润<0识别，窗口与范围按关联原单下单时间及主体口径过滤，仅含支付>0且状态为1、2、3、11、12、13、22、31、101的订单，按利润从低到高排序。"
                +(truncated?"结果已截断至5000笔，笔数为截断后数量，超出部分未包含；":"")
                +"诱因为确定性阈值判定（平台扣点+营销+其他费用占销售额>30%记费用占比过高、运费占销售额>30%记运费占比过高、采购成本为0且销售额>0记零成本待核实），可多命中并列，不代表唯一根因；销售额为0时跳过占比判断。"
                +"结算成本与费用完整性限制同结算利润口径：无出库批次成本记录的订单结算成本为零，未登记费用不代表为零，退款未纳入结算利润。由人核对和作最终判断。");
    }

    /**
     * 多店对比：订单数/支付额用财务 cohort（同 overview 支付口径），退款复用唯一匹配规则归集到原单店铺，
     * 结算列按店铺聚合已审核结算单；HEADQUARTERS/MERCHANT 多店，SHOP 单店，仓库/供应商走 bound() 拒绝。
     */
    @Transactional(readOnly=true)
    public ShopComparisonResult shopComparison(int days,BusinessSubjectScopeService.Scope scope) {
        if(days!=7 && days!=30) throw new IllegalArgumentException("仅支持7或30个完整自然日");
        var orderScope=bound(scope,"o");
        var end=LocalDate.now().atStartOfDay();var start=end.minusDays(days);
        String cohortWhere="o.order_time>=? AND o.order_time<?"+orderScope.sql();
        String cohort=" FROM o_order o WHERE "+cohortWhere;
        String paid=" AND o.payment>0 AND o.order_status IN (1,2,3,11,12,13,22,31,101)";
        var values=args(start,end,orderScope.args());
        // 订单数与支付额：财务 cohort 按店铺分组。
        record BaseAgg(long orders, BigDecimal payment) {}
        var base=new java.util.LinkedHashMap<String,BaseAgg>();
        jdbc.query("SELECT o.shop_id,COUNT(*),COALESCE(SUM(CAST(o.payment AS DECIMAL(20,2))),0)"+cohort+paid+" GROUP BY o.shop_id",
                rs->{ while(rs.next()) base.put(rs.getString(1),new BaseAgg(rs.getLong(2),rs.getBigDecimal(3))); return null; },values);
        // 退款：单号+商户+店铺唯一匹配原单，归集到原单店铺。
        String match="o.order_num=r.order_num AND o.merchant_id=r.merchant_id AND o.shop_id=r.shop_id";
        String matches="(SELECT COUNT(*) FROM o_order x WHERE x.order_num=r.order_num AND x.merchant_id=r.merchant_id AND x.shop_id=r.shop_id)";
        String refundFrom=" FROM o_refund r JOIN o_order o ON "+match+" WHERE o.order_time>=? AND o.order_time<?"+orderScope.sql()+paid
                +" AND r.status IN ("+REFUNDED_STATUS+") AND r.refund_type IN (1,10,11) AND r.create_time<? AND "+matches+"=1";
        var refundArgs=new ArrayList<Object>(List.of(start,end));refundArgs.addAll(orderScope.args());refundArgs.add(end);
        var refunds=new java.util.HashMap<String,BigDecimal>();
        jdbc.query("SELECT o.shop_id,COALESCE(SUM(CASE WHEN r.refund_fee>=0 THEN CAST(r.refund_fee AS DECIMAL(20,2)) ELSE 0 END),0)"+refundFrom
                        +" GROUP BY o.shop_id",
                rs->{ while(rs.next()) refunds.put(rs.getString(1),rs.getBigDecimal(2)); return null; },refundArgs.toArray());
        // 结算：已审核结算单按店铺聚合（仅当结算表存在）；口径与 settlement()/lossOrders 完全一致（含 paid 过滤）。
        record SettledAgg(long count, BigDecimal sales, BigDecimal profit) {}
        var settledByShop=new java.util.HashMap<String,SettledAgg>();
        if(settlementTableExists()) {
            String settledFrom=" FROM fms_order_settlement s JOIN o_order o ON o.id=s.order_id WHERE s.status=1 AND "+cohortWhere+paid;
            jdbc.query("SELECT o.shop_id,COUNT(*),COALESCE(SUM(CAST(s.sales_amount AS DECIMAL(20,2))),0),COALESCE(SUM(CAST(s.profit AS DECIMAL(20,2))),0)"
                            +settledFrom+" GROUP BY o.shop_id",
                    rs->{ while(rs.next()) settledByShop.put(rs.getString(1),new SettledAgg(rs.getLong(2),rs.getBigDecimal(3),rs.getBigDecimal(4))); return null; },values);
        }
        var shops=new ArrayList<ShopComparison>(base.size());
        base.forEach((shopId,agg)->{
            var settled=settledByShop.get(shopId);
            shops.add(new ShopComparison(shopId,agg.orders(),agg.payment(),refunds.getOrDefault(shopId,BigDecimal.ZERO),
                    settled==null?0L:settled.count(),settled==null?null:settled.sales(),settled==null||settled.count()==0?null:settled.profit()));
        });
        shops.sort(Comparator.comparing(ShopComparison::payment).reversed().thenComparing(ShopComparison::shopId,Comparator.nullsLast(Comparator.naturalOrder())));
        String scopeNote=scope.type().equals("SHOP")?"店铺账号仅显示绑定的单个店铺；":"";
        return new ShopComparisonResult(days,start,end,LocalDateTime.now(),shops,
                "订单数与支付额为原单支付口径（payment>0且状态为1、2、3、11、12、13、22、31、101），按下单时间取完整自然日，与销售助手的有效销售口径不同。"
                +"退款按单号+商户+店铺唯一匹配原单后归集到原单店铺，仅含已完成（本地已办结4、平台退款完成10010、售后成功10020）的类型1/10/11退款且申请时间早于窗口结束；未匹配退款不参与分摊。"
                +"结算列为已审核结算单聚合（不含退款调整），原单须满足与结算利润/亏损穿透相同的支付口径（payment>0且状态为1、2、3、11、12、13、22、31、101），未结算订单不计入；结算表缺失或该店窗口内无已审核结算单时利润显示不可用，不代表零利润。"
                +scopeNote+"各店铺金额未换汇，不等于净收入。由人核对和作最终判断。");
    }
}
