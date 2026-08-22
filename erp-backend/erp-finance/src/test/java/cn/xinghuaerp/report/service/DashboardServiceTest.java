package cn.xinghuaerp.report.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import cn.xinghuaerp.report.mapper.DashboardMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Actual aggregate SQL against isolated H2; no user database or AI calls. */
class DashboardServiceTest {
    DashboardService service;
    JdbcTemplate db;
    @BeforeEach void setup() throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=VALUE,DAY;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(ds);
        db.execute("CREATE TABLE o_order(id BIGINT,order_num VARCHAR(50),merchant_id BIGINT,shop_id BIGINT,order_time TIMESTAMP,payment DOUBLE,order_status INT)");
        db.execute("CREATE TABLE o_refund(id BIGINT,order_num VARCHAR(50),merchant_id BIGINT,shop_id BIGINT,create_time TIMESTAMP,refund_type INT,status INT,refund_fee DOUBLE)");
        db.execute("CREATE TABLE o_shop(id BIGINT,name VARCHAR(50))");
        db.execute("CREATE TABLE o_goods(id BIGINT,name VARCHAR(50),image VARCHAR(255))");
        db.execute("CREATE TABLE o_order_item(order_id BIGINT,goods_id BIGINT,goods_title VARCHAR(50),goods_img VARCHAR(255),payment DOUBLE,item_amount DOUBLE,quantity INT)");
        db.execute("CREATE TABLE erp_warehouse(id BIGINT,merchant_id BIGINT,warehouse_name VARCHAR(50))");
        db.execute("CREATE TABLE o_goods_sku(id BIGINT,low_qty INT)");
        db.execute("CREATE TABLE o_goods_inventory(id BIGINT,warehouse_id BIGINT,merchant_id BIGINT,shop_id BIGINT,sku_id BIGINT,quantity INT,available_quantity INT,locked_quantity INT,is_delete INT)");
        db.execute("CREATE TABLE erp_stock_in(status INT,merchant_id BIGINT,shop_id BIGINT)");
        db.execute("CREATE TABLE erp_stock_out(status INT,merchant_id BIGINT,shop_id BIGINT)");
        db.update("INSERT INTO o_shop VALUES(10,'总部店'),(20,'商户店')");db.update("INSERT INTO o_goods VALUES(1,'电饭煲','image1'),(2,'电水壶','image2')");
        db.update("INSERT INTO o_order VALUES(1,'S1',0,10,'2026-10-21 08:00:00',100,3),(2,'S2',101,20,'2026-10-27 08:00:00',200,3),(3,'S3',101,20,'2026-10-27 09:00:00',50,1),(4,'OLD',0,10,'2026-10-20 08:00:00',999,1),(5,'FUTURE',0,10,'2026-10-28 00:00:00',999,3),(6,'UNPAID',0,10,'2026-10-27 10:00:00',999,21)");
        db.update("INSERT INTO o_refund VALUES(1,'S1',0,10,'2026-10-22 10:00:00',10,4,10),(2,'S1',0,10,'2026-10-23 10:00:00',11,4,20),(3,'S2',101,20,'2026-10-27 11:00:00',10,0,99),(4,'S2',101,20,'2026-10-27 11:00:00',11,1,1),(5,'S2',0,10,'2026-10-27 11:00:00',11,4,88),(6,'OLD',0,10,'2026-10-27 11:00:00',11,4,77)");
        db.update("INSERT INTO o_order_item VALUES(1,1,'电饭煲','image1',60,60,1),(1,2,'电水壶','image2',40,40,2),(2,1,'电饭煲','image1',200,200,4),(3,2,'电水壶','image2',50,50,1)");
        db.update("INSERT INTO erp_warehouse VALUES(1,0,'总部仓'),(2,101,'商户仓'),(3,102,'其他商户仓')");db.update("INSERT INTO o_goods_sku VALUES(1,75),(2,0)");
        db.update("INSERT INTO o_goods_inventory VALUES(1,1,0,0,1,80,70,10,0),(2,2,101,0,1,20,20,0,0),(3,3,102,0,2,300,300,0,0),(4,2,101,20,2,5,5,0,0),(5,1,0,0,1,999,999,0,1)");
        db.update("INSERT INTO erp_stock_in VALUES(0,0,0),(1,101,20),(2,101,20)");db.update("INSERT INTO erp_stock_out VALUES(1,101,20),(2,0,0)");
        var config=new MybatisConfiguration();config.addMapper(DashboardMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);factory.setMapperLocations(new ClassPathResource("mapper/report/DashboardMapper.xml"));
        service=new DashboardService(new SqlSessionTemplate(factory.getObject()).getMapper(DashboardMapper.class),Clock.fixed(Instant.parse("2026-10-27T04:00:00Z"),ZoneId.of("Asia/Shanghai")));
    }
    @AfterEach void close(){db.execute("SHUTDOWN");}
    @Test void cohortTotalsDoNotMultiplyOrdersForMultipleRefundsAndFillMissingDates() {
        var result=service.overview(7,null,null,"全部");var s=result.summary();
        assertEquals(3,s.orderCount());assertEquals(new BigDecimal("350.00"),s.grossAmount());assertEquals(new BigDecimal("30.00"),s.refundAmount());assertEquals(new BigDecimal("320.00"),s.netAmount());assertEquals(3,s.afterSaleCount());
        assertEquals(7,result.daily().size());assertEquals(LocalDate.of(2026,10,21),result.daily().get(0).date());assertEquals(new BigDecimal("70.00"),result.daily().get(0).netAmount());assertEquals(0,result.daily().get(1).orderCount());
        assertEquals(new BigDecimal("260.00"),result.products().get(0).amount());assertEquals(405,s.stockQuantity());assertEquals(395,s.availableQuantity());assertEquals(2,s.lowStockCount());
        assertEquals(2,result.tasks().waitShip());assertEquals(2,result.tasks().waitInbound());assertEquals(1,result.tasks().waitOutbound());assertEquals(1,result.tasks().waitAfterSale());
    }
    @Test void merchantScopeAppliesToEveryChartAndCurrentInventory() {
        var result=service.overview(7,101L,null,"商户");assertEquals(2,result.summary().orderCount());assertEquals(new BigDecimal("250.00"),result.summary().grossAmount());assertEquals(25,result.summary().stockQuantity());assertEquals(1,result.warehouses().size());assertEquals("2",result.warehouses().get(0).id());assertEquals(1,result.shops().size());assertEquals("20",result.shops().get(0).id());assertEquals(1,result.summary().afterSaleCount());assertEquals(1,result.tasks().waitShip());
    }
    @Test void shopScopeDoesNotExposeSharedWarehouseStockOrOtherShops() {
        var result=service.overview(7,null,20L,"店铺");assertEquals(2,result.summary().orderCount());assertEquals(5,result.summary().stockQuantity());assertEquals(1,result.warehouses().size());assertEquals(1,result.tasks().waitInbound());assertEquals(1,result.tasks().waitOutbound());
    }
    @Test void emptyScopeHasThirtyRealZeroDaysAndNoFabricatedData() {
        var result=service.overview(30,404L,null,"空商户");assertEquals(30,result.daily().size());assertEquals(0,result.summary().orderCount());assertEquals(new BigDecimal("0.00"),result.summary().averageOrderAmount());assertTrue(result.products().isEmpty());assertTrue(result.orderStatuses().isEmpty());assertTrue(result.warehouses().isEmpty());
    }
    @Test void invalidWindowIsRejected(){assertThrows(IllegalArgumentException.class,()->service.overview(365,null,null,"全部"));}
}
