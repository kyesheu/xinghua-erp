package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.product.entity.ErpSupplierProductItem;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrder;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrderItem;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.purchase.entity.ErpSupplierProduct;
import cn.xinghuaerp.ai.service.AiPurchaseDraftService.Request;
import cn.xinghuaerp.product.mapper.OGoodsSkuMapper;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderItemMapper;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.product.mapper.ErpSupplierProductItemMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierProductMapper;
import cn.xinghuaerp.purchase.service.ErpPurchaseOrderService;
import cn.xinghuaerp.purchase.service.impl.ErpPurchaseOrderServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.junit.jupiter.api.Assertions.*;

class AiPurchaseDraftIntegrationTest {
    JdbcTemplate db;
    AiPurchaseDraftService drafts;
    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("ai-sales-shipping-schema.sql")).execute(ds);
        db = new JdbcTemplate(ds);
        var config = new MybatisConfiguration();
        for (var type : List.of(ErpPurchaseOrderMapper.class,ErpPurchaseOrderItemMapper.class,OGoodsSkuMapper.class,
                ErpSupplierMapper.class,ErpSupplierProductMapper.class,ErpSupplierProductItemMapper.class)) config.addMapper(type);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        createTable("erp_supplier_product",ErpSupplierProduct.class);
        createTable("erp_supplier",ErpSupplier.class); createTable("erp_supplier_product_item",ErpSupplierProductItem.class);
        createTable("erp_purchase_order",ErpPurchaseOrder.class); createTable("erp_purchase_order_item",ErpPurchaseOrderItem.class);
        db.execute("CREATE TABLE ai_purchase_draft_request(request_key VARCHAR(36) PRIMARY KEY,user_id BIGINT NOT NULL,sku_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,quantity BIGINT NOT NULL,unit_price DECIMAL(8,2) NOT NULL,purchase_order_id BIGINT)");
        db.execute("CREATE TABLE ai_inventory_review_task(id VARCHAR(36) PRIMARY KEY,sku_id BIGINT,suggested_quantity INT,approved_quantity INT,status VARCHAR(16),version INT)");
        db.update("INSERT INTO erp_supplier(id,name,disable,is_delete) VALUES(10,'测试供应商',0,0)");
        db.update("INSERT INTO erp_supplier_product(id,supplier_id,status) VALUES(1,10,1)");
        db.update("INSERT INTO erp_supplier_product_item(id,supplier_product_id,supplier_id,erp_goods_sku_id,status) VALUES(1,1,10,101,1)");
        db.update("INSERT INTO o_goods_sku(id,goods_id,sku_code,goods_name,inventory_mode,ship_type,merchant_id) VALUES(101,201,'SKU-101','测试商品',0,10,0)");
        var manager = new DataSourceTransactionManager(ds);
        var productFacade = new cn.xinghuaerp.product.facade.ProductFacadeImpl(session.getMapper(ErpSupplierProductItemMapper.class), null, session.getMapper(OGoodsSkuMapper.class), null, null);
        var purchase = new ErpPurchaseOrderServiceImpl(session.getMapper(ErpPurchaseOrderMapper.class),session.getMapper(ErpPurchaseOrderItemMapper.class),null,
                productFacade, productFacade, session.getMapper(ErpSupplierMapper.class));
        var orderProxy = new ProxyFactory(purchase); orderProxy.setProxyTargetClass(true);
        orderProxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        var target = new AiPurchaseDraftService(db,(ErpPurchaseOrderService)orderProxy.getProxy());
        var draftProxy = new ProxyFactory(target); draftProxy.setProxyTargetClass(true);
        draftProxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        drafts = (AiPurchaseDraftService)draftProxy.getProxy();
    }
    void createTable(String name, Class<?> type) {
        var fields = TableInfoHelper.getTableInfo(type).getFieldList().stream().map(f -> f.getColumn()+" "+sqlType(f.getPropertyType())).collect(Collectors.joining(","));
        db.execute("CREATE TABLE "+name+" (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"+fields+")");
    }
    String sqlType(Class<?> type) {
        if (type == LocalDateTime.class) return "TIMESTAMP";
        if (type == BigDecimal.class) return "DECIMAL(12,2)";
        if (Number.class.isAssignableFrom(type)) return "BIGINT";
        return "VARCHAR(500)";
    }
    @AfterEach void close() { db.execute("SHUTDOWN"); }
    @Test void createsRealDraftAndRetriesWithoutSecondOrder() {
        var request=new AiPurchaseDraftService.Request(UUID.randomUUID().toString(),101L,10L,3L,new BigDecimal("9.50"));
        var first=drafts.create(request,7L,"tester"); var second=drafts.create(request,7L,"tester");
        assertEquals(first.orderId(),second.orderId()); assertTrue(second.replayed());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM erp_purchase_order",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM erp_purchase_order_item",Integer.class));
        assertEquals(0,db.queryForObject("SELECT status FROM erp_purchase_order",Integer.class));
        assertEquals(first.orderId(),db.queryForObject("SELECT purchase_order_id FROM ai_purchase_draft_request",Long.class));
    }
    @Test void failedItemWriteRollsBackRealOrderAndRequest() {
        db.execute("ALTER TABLE erp_purchase_order_item ADD CONSTRAINT fail_item CHECK (quantity < 0)");
        var request=new AiPurchaseDraftService.Request(UUID.randomUUID().toString(),101L,10L,3L,new BigDecimal("9.50"));
        assertThrows(RuntimeException.class,()->drafts.create(request,7L,"tester"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM erp_purchase_order",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ai_purchase_draft_request",Integer.class));
    }
    @Test void approvedReviewBindsDraftToTaskAndRejectsWrongVersion() {
        String task = UUID.randomUUID().toString();
        db.update("INSERT INTO ai_inventory_review_task(id,sku_id,suggested_quantity,approved_quantity,status,version) VALUES(?,101,3,7,'APPROVED',2)",task);
        assertThrows(IllegalStateException.class,()->drafts.createFromReview(task,1,10L,new BigDecimal("9.50"),7L,"tester"));
        var first = drafts.createFromReview(task,2,10L,new BigDecimal("9.50"),7L,"tester");
        var replay = drafts.createFromReview(task,2,10L,new BigDecimal("9.50"),7L,"tester");
        assertEquals(first.orderId(),replay.orderId());
        assertTrue(replay.replayed());
        assertEquals(first.orderId(),drafts.findReviewedDraft(task,7L,10L,new BigDecimal("9.50")).orderId());
        assertThrows(IllegalArgumentException.class,()->drafts.findReviewedDraft(task,7L,10L,new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class,()->drafts.findReviewedDraft(task,8L,10L,new BigDecimal("9.50")));
        assertThrows(IllegalArgumentException.class,()->drafts.findReviewedDraft(task,7L,11L,new BigDecimal("9.50")));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM erp_purchase_order",Integer.class));
        assertEquals(7L,db.queryForObject("SELECT quantity FROM ai_purchase_draft_request WHERE request_key=?",Long.class,task));
        assertEquals(101L,db.queryForObject("SELECT sku_id FROM ai_purchase_draft_request WHERE request_key=?",Long.class,task));
    }
    @Test void failedReviewedDraftRollsBackRequestAndKeepsReview() {
        String task = UUID.randomUUID().toString();
        db.update("INSERT INTO ai_inventory_review_task(id,sku_id,suggested_quantity,approved_quantity,status,version) VALUES(?,101,3,3,'APPROVED',2)",task);
        db.execute("ALTER TABLE erp_purchase_order_item ADD CONSTRAINT fail_review_item CHECK (quantity < 0)");
        assertThrows(RuntimeException.class,()->drafts.createFromReview(task,2,10L,new BigDecimal("9.50"),7L,"tester"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM erp_purchase_order",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ai_purchase_draft_request",Integer.class));
        assertEquals("APPROVED",db.queryForObject("SELECT status FROM ai_inventory_review_task WHERE id=?",String.class,task));
        db.execute("ALTER TABLE erp_purchase_order_item DROP CONSTRAINT fail_review_item");
        var retried = drafts.createFromReview(task,2,10L,new BigDecimal("9.50"),7L,"tester");
        assertFalse(retried.replayed());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM erp_purchase_order",Integer.class));
    }
    @Test void rejectedReviewCannotCreateDraft() {
        String task = UUID.randomUUID().toString();
        db.update("INSERT INTO ai_inventory_review_task(id,sku_id,suggested_quantity,status,version) VALUES(?,101,3,'REJECTED',2)",task);
        assertThrows(IllegalStateException.class,()->drafts.createFromReview(task,2,10L,new BigDecimal("9.50"),7L,"tester"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM erp_purchase_order",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ai_purchase_draft_request",Integer.class));
    }
}
