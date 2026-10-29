package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import cn.xinghuaerp.ai.service.AiSalesReadService.Day;
import cn.xinghuaerp.ai.service.AiSalesReadService.Status;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.open.wei.model.Product;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

class AiSalesReadServiceTest {
    JdbcTemplate db;
    AiSalesReadService service;
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(ds);service=new AiSalesReadService(db);
        db.execute("CREATE TABLE o_order(id BIGINT,merchant_id BIGINT,shop_id BIGINT,order_time TIMESTAMP,payment DECIMAL(12,2),order_status INT)");
        db.execute("CREATE TABLE o_order_item(order_id BIGINT,goods_sku_id BIGINT,goods_title VARCHAR(80),quantity INT)");
        var yesterday=LocalDate.now().minusDays(1).atStartOfDay();
        db.update("INSERT INTO o_order VALUES(1,7,10,?,10,1),(2,7,11,?,20,3),(3,8,12,?,30,2),(4,7,10,?,100,11),(5,7,10,?,999,1),(6,7,10,?,7,3)",
                yesterday,yesterday,yesterday,yesterday,LocalDate.now().atStartOfDay(),LocalDate.now().minusDays(8).atStartOfDay());
        db.update("INSERT INTO o_order_item VALUES(1,5,'A',2),(1,6,'B',3),(1,NULL,'UNLINKED',4),(1,7,'INVALID',-1),(2,5,'A',8),(3,5,'A',9)");
    }
    @AfterEach void close() { db.execute("SHUTDOWN"); }
    @Test void shopScopeAvoidsPaymentJoinMultiplicationAndReportsMissingSkuData() {
        var result=service.overview(7,new BusinessSubjectScopeService.Scope("SHOP",10L,7L,10L));
        assertEquals(2,result.current().orders());assertEquals(1,result.current().paidOrders());
        assertEquals(0,new BigDecimal("10").compareTo(result.current().payment()));
        assertEquals(1,result.previous().paidOrders());assertEquals(0,new BigDecimal("7").compareTo(result.previous().payment()));
        assertEquals(7,result.trend().size());assertEquals(1,result.trend().stream().mapToLong(AiSalesReadService.Day::orders).sum());
        assertEquals(2,result.products().size());assertEquals(5,result.products().stream().mapToLong(AiSalesReadService.Product::quantity).sum());
        assertEquals(1,result.quality().unlinkedRows());assertEquals(1,result.quality().invalidQuantityRows());
        assertEquals(2,result.statuses().stream().mapToLong(AiSalesReadService.Status::orders).sum());
        assertEquals("10",result.shops().get(0).shopId());
    }
    @Test void merchantAndHeadquartersScopesAreExplicitAndOtherSubjectsFailClosed() {
        assertEquals(2,service.overview(7,new BusinessSubjectScopeService.Scope("MERCHANT",7L,7L,null)).current().paidOrders());
        assertEquals(3,service.overview(7,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null)).current().paidOrders());
        assertThrows(IllegalArgumentException.class,()->service.overview(7,new BusinessSubjectScopeService.Scope("WAREHOUSE",1L,null,null)));
        assertThrows(IllegalArgumentException.class,()->service.overview(7,new BusinessSubjectScopeService.Scope("SHOP",10L,null,10L)));
        assertThrows(IllegalArgumentException.class,()->service.overview(8,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null)));
    }
    @Test void emptyAuthorizedScopeIsZeroWithCompleteCalendar() {
        var result=service.overview(30,new BusinessSubjectScopeService.Scope("SHOP",99L,7L,99L));
        assertEquals(0,result.current().orders());assertEquals(30,result.trend().size());
        assertTrue(result.products().isEmpty());assertTrue(result.shops().isEmpty());
    }

    // ---- SKU 表现排行完整版（skuPerformance）----

    /** 补齐 skuPerformance 所需列与表：o_order.order_num（退款唯一匹配）、o_order_item.payment（明细金额）、o_refund（退款归集）。 */
    private void enableSkuPerformance() {
        db.execute("ALTER TABLE o_order ADD COLUMN order_num VARCHAR(30)");
        db.execute("ALTER TABLE o_order_item ADD COLUMN payment DECIMAL(12,2)");
        db.execute("CREATE TABLE o_refund(id BIGINT,order_num VARCHAR(30),merchant_id BIGINT,shop_id BIGINT,goods_sku_id BIGINT,"
                +"create_time TIMESTAMP,refund_fee DECIMAL(12,2),status INT,refund_type INT)");
        db.update("DELETE FROM o_order");db.update("DELETE FROM o_order_item");
    }
    private void money(String expected,BigDecimal actual) { assertEquals(0,new BigDecimal(expected).compareTo(actual)); }

    /** 聚合正确：PAID 订单明细聚合；退款按唯一匹配原单+退款单登记 SKU 归集；未关联退款计数；未分类退款不进行。 */
    @Test void skuPerformanceAggregatesSalesAndRefunds() {
        enableSkuPerformance();
        var day=LocalDate.now().minusDays(1).atStartOfDay();
        db.update("INSERT INTO o_order VALUES(1,7,10,?,100,3,'O1'),(2,7,11,?,50,3,'O2'),(3,7,10,?,100,11,'O3')",day,day,day);
        db.update("INSERT INTO o_order_item VALUES(1,1,'A',2,40),(1,2,'B',1,60),(2,1,'A',1,50),(3,3,'C',5,100)");
        // r101/r105 归集到 SKU1；r102 的 SKU3 无有效销售行不进结果；r103 未关联计数；r104 状态未完成不计。
        db.update("INSERT INTO o_refund VALUES(101,'O1',7,10,1,?,30,4,10),(102,'O1',7,10,3,?,10,4,10),"
                +"(103,'O1',7,10,NULL,?,99,4,11),(104,'O1',7,10,2,?,5,14000,10),(105,'O2',7,11,1,?,5,4,10)",day,day,day,day,day);
        var result=service.skuPerformance(7,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null),100);
        assertEquals(2,result.skuCount());assertEquals(1,result.unlinkedRefundRows());
        assertEquals(2,result.rows().size());
        var sku1=result.rows().stream().filter(r->r.skuId().equals("1")).findFirst().orElseThrow();
        assertEquals(2,sku1.paidOrders());assertEquals(3,sku1.quantity());money("90",sku1.salesAmount());
        assertEquals(2,sku1.refundOrders());money("35",sku1.refundAmount());
        money("0.388889",sku1.refundRate());assertEquals("highSalesHighRefund",sku1.quadrant());
        var sku2=result.rows().stream().filter(r->r.skuId().equals("2")).findFirst().orElseThrow();
        assertEquals(0,sku2.refundOrders());money("0",sku2.refundAmount());money("0",sku2.refundRate());
        assertEquals("lowSalesLowRefund",sku2.quadrant());
        assertEquals(1000,result.topShares().get("top5"));assertEquals(1000,result.topShares().get("top20"));
        assertEquals(2,result.quadrantCounts().get("highSalesHighRefund")+result.quadrantCounts().get("lowSalesLowRefund"));
        // SKU3 的退款（r102）匹配唯一原单但窗口内无销售行：不进结果、不计异常，limitation 声明该丢弃口径。
        assertTrue(result.rows().stream().noneMatch(r->r.skuId().equals("3")));
        assertTrue(result.limitation().contains("无销售记录的SKU退款不计入"));
    }

    /** 中位数四象限：偶数取中间两值平均；等于中位数归高侧（>=）；集中度为百分比×10 整数；rowLimit 截断展示但 skuCount 不变。 */
    @Test void medianQuadrantClassificationAndConcentration() {
        enableSkuPerformance();
        var day=LocalDate.now().minusDays(1).atStartOfDay();
        // 销量 [1,1,1,2,2,4] 中位数 1.5；退款率 [0,0,0.2,0.2,0.4,0.4] 中位数 0.2，SKU3 等于中位数归高侧。
        db.update("INSERT INTO o_order VALUES(1,7,10,?,100,3,'O1'),(2,7,10,?,100,3,'O2'),(3,7,10,?,100,3,'O3'),"
                +"(4,7,10,?,200,3,'O4'),(5,7,10,?,400,3,'O5'),(6,7,10,?,500,3,'O6')",day,day,day,day,day,day);
        db.update("INSERT INTO o_order_item VALUES(1,1,'A',1,100),(2,2,'B',1,100),(3,3,'C',1,100),"
                +"(4,4,'D',2,200),(5,5,'E',2,400),(6,6,'F',4,500)");
        db.update("INSERT INTO o_refund VALUES(301,'O3',7,10,3,?,20,4,10),(304,'O4',7,10,4,?,40,4,10),"
                +"(305,'O5',7,10,5,?,160,4,10),(306,'O6',7,10,6,?,200,4,10)",day,day,day,day);
        var result=service.skuPerformance(7,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null),100);
        money("1.5",result.medianQuantity());money("0.2",result.medianRefundRate());
        // SKU3 rate=0.2 等于中位数归高侧 → lowSalesHighRefund；高销量（qty>=1.5）为 SKU4/5/6。
        assertEquals(3,result.quadrantCounts().get("highSalesHighRefund"));
        assertEquals(1,result.quadrantCounts().get("lowSalesHighRefund"));
        assertEquals(2,result.quadrantCounts().get("lowSalesLowRefund"));
        money("1100",result.quadrantSales().get("highSalesHighRefund"));
        // 集中度：TOP5=1300/1400 → 928.57 → 929；TOP10/TOP20 全量 → 1000。
        assertEquals(929,result.topShares().get("top5"));assertEquals(1000,result.topShares().get("top10"));
        assertEquals(1000,result.topShares().get("top20"));
        assertEquals(6,result.skuCount());
        var limited=service.skuPerformance(7,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null),3);
        assertEquals(6,limited.skuCount());assertEquals(3,limited.rows().size());
        money("500",limited.rows().get(0).salesAmount());   // 按销售额降序
    }

    /** SKU 数不足 2 时不做四象限分类：中位数为 null、分布为空、行为 null，limitation 声明。 */
    @Test void fewerThanTwoSkipsQuadrantClassification() {
        enableSkuPerformance();
        var day=LocalDate.now().minusDays(1).atStartOfDay();
        db.update("INSERT INTO o_order VALUES(1,7,10,?,100,3,'O1')",day);
        db.update("INSERT INTO o_order_item VALUES(1,1,'A',2,40)");
        var result=service.skuPerformance(7,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null),100);
        assertEquals(1,result.skuCount());assertNull(result.medianQuantity());assertNull(result.medianRefundRate());
        assertTrue(result.quadrantCounts().isEmpty());assertTrue(result.quadrantSales().isEmpty());
        assertNull(result.rows().get(0).quadrant());
        assertTrue(result.limitation().contains("商品数量不足"));
    }

    @Test void skuPerformanceValidatesWindowAndScope() {
        enableSkuPerformance();
        assertThrows(IllegalArgumentException.class,()->service.skuPerformance(8,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null),100));
        assertThrows(IllegalArgumentException.class,()->service.skuPerformance(7,new BusinessSubjectScopeService.Scope("WAREHOUSE",1L,null,null),100));
        assertThrows(IllegalArgumentException.class,()->service.skuPerformance(7,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null),0));
    }
    @Test void oversizedSkuAggregationFailsInsteadOfReturningFalseFullTotals() {
        enableSkuPerformance();
        db.update("INSERT INTO o_order VALUES(1,7,10,?,100,3,'O1')", LocalDate.now().minusDays(1).atStartOfDay());
        db.update("INSERT INTO o_order_item SELECT 1,X,'SKU',1,1 FROM SYSTEM_RANGE(1,20001)");
        assertThrows(IllegalArgumentException.class, () -> service.skuPerformance(7,new Scope("MERCHANT",7L,7L,null),100));
    }
}
