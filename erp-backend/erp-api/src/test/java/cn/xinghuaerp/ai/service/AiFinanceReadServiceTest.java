package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

class AiFinanceReadServiceTest {
    JdbcTemplate db;AiFinanceReadService service;
    final BusinessSubjectScopeService.Scope shop=new BusinessSubjectScopeService.Scope("SHOP",10L,7L,10L);
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(ds);service=new AiFinanceReadService(db);
        db.execute("CREATE TABLE o_order(id BIGINT,order_num VARCHAR(30),merchant_id BIGINT,shop_id BIGINT,order_time TIMESTAMP,payment DECIMAL(12,2),order_status INT)");
        db.execute("CREATE TABLE o_refund(order_num VARCHAR(30),merchant_id BIGINT,shop_id BIGINT,create_time TIMESTAMP,refund_fee DECIMAL(12,2),status INT,refund_type INT)");
        var day=LocalDate.now().minusDays(1).atStartOfDay();var today=LocalDate.now().atStartOfDay();
        db.update("INSERT INTO o_order VALUES(1,'A',7,10,?,100,3),(2,'A',7,11,?,80,3),(3,'A',8,12,?,90,3),(4,'C',7,10,?,10,11),(5,'NEG',7,10,?,-3,3),(6,'OLD',7,10,?,100,3)",day,day,day,day,day,today.minusDays(40));
        db.update("INSERT INTO o_refund VALUES('A',7,10,?,20,4,10),('A',7,10,?,30,4,11),('C',7,10,?,12,4,1),('A',7,11,?,70,4,11),('MISSING',7,10,?,900,4,11),('A',7,10,?,NULL,4,11),('A',7,10,?,999,4,11),('A',7,10,?,888,3,11),('OLD',7,10,?,40,4,11)",day,day,day,day,day,day,today,day,day);
    }
    @AfterEach void close(){db.execute("SHUTDOWN");}
    void money(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
    @Test void refundsUseUniqueScopedOriginalOrdersWithoutJoinMultiplication() {
        var result=service.overview(7,shop);
        assertEquals(2,result.orders().count());money("110",result.orders().payment());
        money("62",result.refunds().amount());money("48",result.paymentLessRefund());
        assertEquals(1,result.refunds().invalidRows());assertEquals(1,result.refunds().unmatchedRows());
        assertEquals(1,result.orders().negativePaymentRows());
        assertNull(result.profit());assertFalse(result.ledger().available());assertNull(result.ledger().expense());
    }
    @Test void ambiguousOrderNumberDoesNotMultiplyRefundsAndOverRefundIsNotClamped() {
        var day=LocalDate.now().minusDays(1).atStartOfDay();
        db.update("INSERT INTO o_order VALUES(7,'D',7,10,?,5,3),(8,'D',7,10,?,6,3)",day,day);
        db.update("INSERT INTO o_refund VALUES('D',7,10,?,999,4,11),('A',7,10,?,200,4,11)",day,day);
        var result=service.overview(7,shop);
        assertEquals(1,result.refunds().ambiguousRows());money("262",result.refunds().amount());
        money("-141",result.paymentLessRefund());assertNull(result.profit());
    }
    /**
     * 退款到账口径必须同时覆盖两套状态词表：本地已办结(4)、平台退款完成(10010)；
     * 平台拒绝(14000) 不是退款到账，不得计入。
     *
     * <p>清空共享夹具里的退款行，让「笔数」断言只反映本用例插入的三条（否则会与
     * {@link #refundsUseUniqueScopedOriginalOrdersWithoutJoinMultiplication()} 等用例的数据混算）。</p>
     */
    @Test void platformRefundStatusesCountAsRefundedButRejectedDoesNot() {
        db.update("DELETE FROM o_refund");
        var day=LocalDate.now().minusDays(1).atStartOfDay();
        db.update("INSERT INTO o_refund VALUES('A',7,10,?,10,4,10),('A',7,10,?,20,10010,10),('A',7,10,?,40,14000,10)",day,day,day);
        var result=service.overview(7,shop);
        assertEquals(2,result.refunds().count(),"本地已办结(4)与平台退款完成(10010)各计一笔；平台拒绝(14000)不计");
        money("30",result.refunds().amount());
        money("80",result.paymentLessRefund());

        // 平台售后成功(10020) 同属退款到账，单列一条验证，不改上面 2 笔的断言。
        db.update("INSERT INTO o_refund VALUES('A',7,10,?,5,10020,10)",day);
        var withSuccess=service.overview(7,shop);
        assertEquals(3,withSuccess.refunds().count(),"平台售后成功(10020)也算退款到账");
        money("35",withSuccess.refunds().amount());
    }
    @Test void ledgerIsIndependentScopedAndInvalidAmountsAreFlagged() {
        db.execute("CREATE TABLE fms_finance_ledger(account_type VARCHAR(30),merchant_id BIGINT,shop_id BIGINT,expense_date TIMESTAMP,income_amount DECIMAL(12,2),expense_amount DECIMAL(12,2))");
        var day=LocalDate.now().minusDays(1).atStartOfDay();
        db.update("INSERT INTO fms_finance_ledger VALUES('shipping',7,10,?,0,12),('shipping',7,11,?,0,999),('bad',7,10,?,0,-1)",day,day,day);
        var result=service.overview(7,shop);
        assertTrue(result.ledger().available());assertEquals(2,result.ledger().entries());assertEquals(1,result.ledger().invalidRows());
        money("12",result.ledger().expense());money("48",result.paymentLessRefund());assertNull(result.profit());
        assertEquals(1,result.ledger().accounts().size());
    }
    @Test void floatingRefundStorageIsRoundedPerRecordBeforeAggregation() {
        db.execute("ALTER TABLE o_refund ALTER COLUMN refund_fee REAL");
        db.update("UPDATE o_refund SET refund_fee=20.2 WHERE refund_fee=20");
        var result=service.overview(7,shop);
        money("62.20",result.refunds().amount());money("47.80",result.paymentLessRefund());
        assertEquals(2,result.refunds().amount().scale());
    }
    @Test void scopeAndEmptyRangeRemainExplicit() {
        var empty=service.overview(30,new BusinessSubjectScopeService.Scope("SHOP",99L,7L,99L));
        money("0",empty.orders().payment());assertNull(empty.profit());
        assertThrows(IllegalArgumentException.class,()->service.overview(7,new BusinessSubjectScopeService.Scope("WAREHOUSE",1L,null,null)));
        assertThrows(IllegalArgumentException.class,()->service.overview(7,new BusinessSubjectScopeService.Scope("SHOP",10L,null,10L)));
        assertThrows(IllegalArgumentException.class,()->service.overview(8,shop));
    }

    // ---- 结算利润聚合（已审核结算单口径）----

    private void createSettlementTable(){
        db.execute("CREATE TABLE fms_order_settlement(id BIGINT AUTO_INCREMENT,order_id BIGINT,sales_amount DECIMAL(12,2),purchase_cost DECIMAL(12,2),"
                +"shipping_fee DECIMAL(12,2),platform_fee DECIMAL(12,2),marketing_fee DECIMAL(12,2),other_fee DECIMAL(12,2),"
                +"total_cost DECIMAL(12,2),profit DECIMAL(12,2),status INT)");
    }
    private void insertSettlement(long orderId,String sales,String purchase,String shipping,String platform,String marketing,String other,String total,String profit,int status){
        db.update("INSERT INTO fms_order_settlement(order_id,sales_amount,purchase_cost,shipping_fee,platform_fee,marketing_fee,other_fee,total_cost,profit,status) VALUES(?,?,?,?,?,?,?,?,?,?)",
                orderId,new BigDecimal(sales),new BigDecimal(purchase),new BigDecimal(shipping),new BigDecimal(platform),new BigDecimal(marketing),new BigDecimal(other),new BigDecimal(total),new BigDecimal(profit),status);
    }
    /** 无结算表时保持原缺失说明，利润为 null。 */
    @Test void missingSettlementTableKeepsProfitNullWithOriginalMissingInputs() {
        var result=service.overview(7,shop);
        assertNull(result.profit());assertNotNull(result.settlement());assertFalse(result.settlement().available());
        assertTrue(result.missingInputs().stream().anyMatch(m->m.contains("历史订单出库时成本快照")));
    }
    /** 结算聚合按原单窗口、主体范围与已付订单口径过滤；利润字段仅在窗口内有已审核结算单时输出。 */
    @Test void settlementProfitAggregatesWindowScopeAndCohort() {
        createSettlementTable();
        var day=LocalDate.now().minusDays(1).atStartOfDay();
        insertSettlement(1,"100","40","5","2","1","0","48","52",1);
        insertSettlement(2,"80","10","1","1","0","0","12","68",1);   // 店铺11，范围外
        insertSettlement(6,"100","10","0","0","0","0","10","90",1);  // 窗口外原单
        insertSettlement(1,"1","0","0","0","0","0","0","1",2);       // 非已审核状态
        var result=service.overview(7,shop);
        assertTrue(result.settlement().available());assertEquals(1,result.settlement().settledOrders());
        money("100",result.settlement().salesAmount());money("40",result.settlement().purchaseCost());
        money("48",result.settlement().totalCost());money("52",result.settlement().profit());
        money("52",result.profit());
        assertEquals(0,result.settlement().zeroCostSettled());
        // 范围内已付口径订单为 1 和 4，仅 1 已结算
        assertEquals(1,result.settlement().unsettledPaidOrders());
        assertTrue(result.missingInputs().stream().anyMatch(m->m.contains("结算成本来自出库批次登记")));
        assertTrue(result.limitation().contains("结算利润"));
    }
    /** 成本为零的结算单单列提示；未结算订单不计入利润，不代表零利润。 */
    @Test void zeroCostSettlementsAreFlaggedAndUnsettledNotCounted() {
        createSettlementTable();
        insertSettlement(1,"100","40","5","2","1","0","48","52",1);
        insertSettlement(4,"10","0","0","0","0","0","0","10",1);     // 无批次成本，结算成本为0
        var result=service.overview(7,shop);
        assertEquals(2,result.settlement().settledOrders());assertEquals(1,result.settlement().zeroCostSettled());
        money("62",result.settlement().profit());money("62",result.profit());
        assertEquals(0,result.settlement().unsettledPaidOrders());
        assertTrue(result.missingInputs().stream().anyMatch(m->m.contains("需人工核实")));
    }

    // ---- 订单级亏损穿透（lossOrders）----

    /** 诱因判定：多命中并列、销售额为0跳过占比、cohort 过滤（范围/窗口/状态）、causeCounts 与明细一致、按利润升序。 */
    @Test void lossOrdersCohortCausesAndCountsAreConsistent() {
        createSettlementTable();
        var day=LocalDate.now().minusDays(1).atStartOfDay();
        // 原单1（店铺10窗口内）：三诱因并列命中——费用占比、运费占比、零成本待核实。
        insertSettlement(1,"100","0","40","30","20","15","105","-5",1);
        // 原单4（店铺10状态11）：仅费用占比命中（费用6/10=60%>30%，运费10%不超阈值，采购成本非0）。
        insertSettlement(4,"10","5","1","2","1","3","11","-1",1);
        insertSettlement(2,"100","50","0","0","0","0","50","-50",1); // 店铺11，范围外
        insertSettlement(6,"100","0","0","0","0","0","0","-100",1);  // 原单窗口外
        insertSettlement(1,"100","0","0","0","0","0","0","10",2);    // 非已审核状态
        var result=service.lossOrders(7,shop);
        assertTrue(result.available());assertEquals(2,result.count());assertFalse(result.truncated());
        assertEquals(2,result.orders().size());
        money("-5",result.orders().get(0).profit());       // 利润升序
        assertEquals(List.of("费用占比过高","运费占比过高","零成本待核实"),result.orders().get(0).causes());
        money("100",result.orders().get(0).salesAmount());money("40",result.orders().get(0).shippingFee());
        money("105",result.orders().get(0).totalCost());
        money("-1",result.orders().get(1).profit());
        assertEquals(List.of("费用占比过高"),result.orders().get(1).causes());
        assertEquals(2L,result.causeCounts().get("费用占比过高"));
        assertEquals(1L,result.causeCounts().get("运费占比过高"));
        assertEquals(1L,result.causeCounts().get("零成本待核实"));
        long totalCauses=result.orders().stream().mapToLong(o->o.causes().size()).sum();
        assertEquals(totalCauses,result.causeCounts().values().stream().mapToLong(Long::longValue).sum());
    }

    /** 销售额为0的亏损订单跳过占比判断，诱因为空；无结算表时 available=false 且不产生误导性零值。 */
    @Test void zeroSalesSkipsRatioChecksAndMissingTableStaysUnavailable() {
        createSettlementTable();
        insertSettlement(1,"0","10","0","0","0","0","10","-10",1);
        var result=service.lossOrders(7,shop);
        assertEquals(1,result.count());assertTrue(result.orders().get(0).causes().isEmpty());
        money("-10",result.orders().get(0).profit());
        assertTrue(result.limitation().contains("销售额为0时跳过占比判断"));
        // 结算表缺失：available=false、笔数为0、明细为空，limitation 说明不可用不代表没有亏损。
        var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var other=new JdbcTemplate(ds);other.execute("CREATE TABLE o_order(id BIGINT,order_num VARCHAR(30),merchant_id BIGINT,shop_id BIGINT,order_time TIMESTAMP,payment DECIMAL(12,2),order_status INT)");
        var emptyService=new AiFinanceReadService(other);
        var missing=emptyService.lossOrders(7,shop);
        assertFalse(missing.available());assertEquals(0,missing.count());assertTrue(missing.orders().isEmpty());
        assertTrue(missing.limitation().contains("结算单表尚未建立"));
        other.execute("SHUTDOWN");
    }

    // ---- 多店对比（shopComparison）----

    /** 总部多店：订单/支付为财务 cohort 口径，退款归集到原单店铺，结算按店铺聚合，未结算店铺利润为 null。 */
    @Test void shopComparisonAggregatesPerShopForHeadquarters() {
        createSettlementTable();
        insertSettlement(1,"100","40","5","2","1","0","48","52",1);
        insertSettlement(4,"10","0","0","0","0","0","0","10",1);
        insertSettlement(2,"80","10","0","0","0","0","10","70",1);
        insertSettlement(5,"999","0","0","0","0","0","0","999",1);   // 原单 payment=-3，paid 口径排除，不计入店铺结算列
        var result=service.shopComparison(7,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null));
        assertEquals(3,result.shops().size());   // 店铺 10、11、12
        var shop10=result.shops().stream().filter(s->"10".equals(s.shopId())).findFirst().orElseThrow();
        // 退款 20+30+12：999 行申请时间为窗口结束点被排除，888 行状态未完成，NULL 行计0，MISSING/OLD 不匹配。
        assertEquals(2,shop10.orders());money("110",shop10.payment());money("62",shop10.refundAmount());
        assertEquals(2,shop10.settledOrders());money("110",shop10.settledSalesAmount());money("62",shop10.profit());
        var shop11=result.shops().stream().filter(s->"11".equals(s.shopId())).findFirst().orElseThrow();
        assertEquals(1,shop11.orders());money("80",shop11.payment());money("70",shop11.refundAmount());
        assertEquals(1,shop11.settledOrders());money("70",shop11.profit());
        var shop12=result.shops().stream().filter(s->"12".equals(s.shopId())).findFirst().orElseThrow();
        assertEquals(1,shop12.orders());money("90",shop12.payment());money("0",shop12.refundAmount());
        assertEquals(0,shop12.settledOrders());assertNull(shop12.settledSalesAmount());assertNull(shop12.profit());
        assertTrue(result.limitation().contains("与结算利润/亏损穿透相同的支付口径"));
    }

    /** 商户多店含两店铺；店铺账号仅单店并在 limitation 声明；仓库账号拒绝。 */
    @Test void shopComparisonScopeRulesAreExplicit() {
        createSettlementTable();
        var merchant=service.shopComparison(7,new BusinessSubjectScopeService.Scope("MERCHANT",7L,7L,null));
        assertEquals(2,merchant.shops().size());
        var single=service.shopComparison(7,shop);
        assertEquals(1,single.shops().size());assertEquals("10",single.shops().get(0).shopId());
        assertTrue(single.limitation().contains("单个店铺"));
        assertTrue(merchant.limitation().contains("结算"));
        assertThrows(IllegalArgumentException.class,()->service.shopComparison(7,new BusinessSubjectScopeService.Scope("WAREHOUSE",1L,null,null)));
        assertThrows(IllegalArgumentException.class,()->service.shopComparison(8,shop));
    }

    /** 结算表缺失时结算列为空值而非0，利润显示不可用。 */
    @Test void shopComparisonWithoutSettlementTableKeepsSettledNull() {
        var result=service.shopComparison(7,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null));
        assertEquals(3,result.shops().size());
        result.shops().forEach(s->assertEquals(0,s.settledOrders()));
        result.shops().forEach(s->assertNull(s.profit()));
    }
}
