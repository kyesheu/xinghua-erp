package cn.xinghuaerp.ai.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import cn.xinghuaerp.ai.mapper.AiInventoryMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class AiInventoryReadServiceTest {
    JdbcTemplate db;
    AiInventoryReadService service;
    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE o_goods_inventory(id BIGINT, goods_id BIGINT, goods_num VARCHAR(40), sku_id BIGINT, sku_code VARCHAR(40), goods_name VARCHAR(40), sku_name VARCHAR(40), quantity INT, locked_quantity INT, available_quantity INT, warehouse_id BIGINT, merchant_id BIGINT, shop_id BIGINT, stock_status INT, update_time TIMESTAMP, is_delete INT)");
        db.update("INSERT INTO o_goods_inventory(id,sku_id,warehouse_id,merchant_id,shop_id,quantity,locked_quantity,available_quantity,is_delete) VALUES (1,5,1,7,10,10,2,8,0),(2,5,1,7,11,20,0,20,0),(3,5,2,8,12,30,0,30,0),(4,5,1,7,10,99,0,99,1),(5,6,1,7,10,50,0,50,0)");
        db.update("UPDATE o_goods_inventory SET sku_code='SKU-A', goods_name='测试商品' WHERE id IN (1,2,4)");
        db.update("UPDATE o_goods_inventory SET sku_code='SKU-B', goods_name='秘密商品' WHERE id=3");
        db.update("UPDATE o_goods_inventory SET sku_code='SKU-C', goods_name='其他商品' WHERE id=5");
        db.update("UPDATE o_goods_inventory SET stock_status=1");
        db.execute("CREATE TABLE o_goods_inventory_batch(id BIGINT, inventory_id BIGINT, sku_id BIGINT, warehouse_id BIGINT, merchant_id BIGINT, shop_id BIGINT, stock_status INT, current_qty BIGINT)");
        db.update("INSERT INTO o_goods_inventory_batch VALUES (1,1,5,1,7,10,1,10),(2,2,5,1,7,11,1,20),(3,3,5,2,8,12,1,30),(5,5,6,1,7,10,1,50)");
        db.execute("CREATE TABLE o_goods_sku(id BIGINT PRIMARY KEY,sku_code VARCHAR(40),goods_name VARCHAR(40),sku_name VARCHAR(40),status INT,low_qty INT,merchant_id BIGINT,shop_id BIGINT)");
        db.update("INSERT INTO o_goods_sku VALUES (5,'SKU-A','测试商品','规格',1,10,7,10),(6,'SKU-C','其他商品','规格',1,5,7,10),(7,'SKU-NO','未建库存','规格',1,5,7,10),(8,'SECRET','其他商户','规格',1,2,8,12)");
        db.execute("CREATE TABLE o_order(id BIGINT, merchant_id BIGINT, shop_id BIGINT, order_time TIMESTAMP, payment DECIMAL(10,2), order_status INT)");
        db.execute("CREATE TABLE o_order_item(order_id BIGINT, goods_sku_id BIGINT, quantity INT)");
        var yesterday = LocalDate.now().minusDays(1).atStartOfDay();
        var today = LocalDate.now().atStartOfDay();
        db.update("INSERT INTO o_order VALUES (1,7,10,?,10,1),(2,7,11,?,10,3),(3,8,12,?,10,2),(4,7,10,?,10,11),(5,7,10,?,0,1),(6,7,10,?,10,1)", yesterday,yesterday,yesterday,yesterday,yesterday,today);
        db.update("INSERT INTO o_order_item VALUES (1,5,2),(2,5,3),(3,5,4),(4,5,20),(5,5,30),(6,5,40),(1,6,90)");
        db.execute("CREATE TABLE erp_stock_in(id BIGINT,stock_in_type INT,merchant_id BIGINT,shop_id BIGINT,source_id BIGINT)");
        db.execute("CREATE TABLE erp_stock_in_item(id BIGINT,stock_in_id BIGINT,source_no VARCHAR(50),source_item_id BIGINT,sku_id BIGINT,warehouse_id BIGINT,merchant_id BIGINT,shop_id BIGINT,quantity INT,in_quantity INT,status INT,update_time TIMESTAMP,stock_in_type INT)");
        db.update("INSERT INTO erp_stock_in VALUES (1,1,7,10,100),(2,1,7,11,101),(3,1,8,12,102),(4,2,7,10,103)");
        db.update("INSERT INTO erp_stock_in_item VALUES (1,1,'A',1,5,1,7,10,10,4,1,NULL,1),(2,2,'B',2,5,1,7,11,10,0,0,NULL,1),(3,3,'C',3,5,2,8,12,10,0,0,NULL,1),(4,1,'D',4,5,1,7,10,10,10,2,NULL,1),(5,4,'E',5,5,1,7,10,10,0,0,NULL,2),(6,1,'F',6,5,1,8,12,10,0,0,NULL,1)");
        db.execute("CREATE TABLE erp_purchase_order(id BIGINT,order_num VARCHAR(40),status INT,warehouse_id BIGINT,merchant_id BIGINT,shop_id BIGINT)");
        db.execute("CREATE TABLE erp_purchase_order_item(id BIGINT,order_id BIGINT,spec_id BIGINT,quantity INT,is_delete INT,merchant_id BIGINT,shop_id BIGINT)");
        db.update("INSERT INTO erp_purchase_order VALUES (100,'TASKED',2,1,7,10),(200,'PENDING',102,NULL,7,10),(201,'OTHER',1,NULL,8,12),(202,'CANCEL',99,NULL,7,10),(203,'DRAFT',0,NULL,7,10),(204,'MISMATCH',1,NULL,7,10)");
        db.update("INSERT INTO erp_purchase_order_item VALUES (1,100,5,10,0,7,10),(2,200,5,12,0,7,10),(3,201,5,8,0,8,12),(4,202,5,10,0,7,10),(5,203,5,10,0,7,10),(6,204,5,10,0,8,12)");
        db.execute("ALTER TABLE erp_purchase_order ADD supplier_id BIGINT DEFAULT 9");
        db.execute("CREATE TABLE erp_purchase_order_ship(id BIGINT,order_id BIGINT,supplier_id BIGINT,merchant_id BIGINT,shop_id BIGINT,warehouse_id BIGINT,ship_company VARCHAR(40),ship_num VARCHAR(40),ship_time TIMESTAMP,receipt_time TIMESTAMP,status INT)");
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.addMapper(AiInventoryMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/ai/AiInventoryMapper.xml"));
        service = new AiInventoryReadService(new SqlSessionTemplate(factory.getObject()).getMapper(AiInventoryMapper.class));
    }
    @AfterEach void close() { db.execute("SHUTDOWN"); }
    @Test void riskSummaryIncludesAllPagesAndRespectsShopScope() {
        for (int i=100; i<201; i++)
            db.update("INSERT INTO o_goods_sku VALUES (?, ?, '商品', '规格', 1, 5, 7, 10)",i,"SKU-"+i);
        db.update("INSERT INTO o_goods_sku VALUES (300,'OTHER','其他商户','规格',1,5,8,12)");
        var result = service.riskSummary(new Scope("SHOP",10L,7L,10L));
        assertEquals(104,result.total());
        assertEquals(104,result.risks().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(102,result.risks().get("待核实库存"));
    }
    @Test void dossierCollectsFactsWithinScopeAndKeepsUnavailableDataDistinct() {
        var shop = new Scope("SHOP",10L,7L,10L);
        var dossier = service.dossier(5L,shop,true,true);
        assertEquals(1,dossier.inventory().records().size());
        assertEquals(2,dossier.sales7Days().quantity());
        assertEquals(2,dossier.sales30Days().quantity());
        assertEquals(2,dossier.purchase().ledger().size());
        var restricted = service.dossier(5L,shop,false,false);
        assertNull(restricted.sales7Days()); assertNull(restricted.purchase());
        assertFalse(restricted.salesVisible()); assertFalse(restricted.purchaseVisible());
        var warehouse = service.dossier(5L,new Scope("WAREHOUSE",1L,null,null),true,true);
        assertEquals(2,warehouse.inventory().records().size());
        assertNull(warehouse.sales30Days()); assertNull(warehouse.purchase());
        var row = service.overview(1,50,shop).records().stream().filter(r -> r.getSkuId().equals("5")).findFirst().orElseThrow();
        assertEquals(10L,row.getQuantity()); assertEquals(2L,row.getLockedQuantity());
    }
    @Test void batchAuditChecksOnlyAuthorizedInventoryAndFlagsMislinkedOrMissingBatches() {
        var shop = new Scope("SHOP",10L,7L,10L);
        var audit = service.dossier(5L,shop,false,false).batchAudit();
        assertEquals(1, audit.size()); assertEquals(1L, audit.get(0).getInventoryId());
        assertEquals(10L, audit.get(0).getBatchQuantity()); assertEquals(0L, audit.get(0).getInvalidBatches());
        db.update("INSERT INTO o_goods_inventory_batch VALUES (4,1,5,2,8,12,1,3)");
        audit = service.analysisSnapshot(5L,1L,7,shop,false,false).batchAudit();
        assertEquals(13L, audit.get(0).getBatchQuantity()); assertEquals(1L, audit.get(0).getInvalidBatches());
        db.update("DELETE FROM o_goods_inventory_batch WHERE inventory_id=1");
        assertEquals(0L, service.dossier(5L,shop,false,false).batchAudit().get(0).getBatchCount());
    }
    @Test void overviewPrioritizesScopedBatchIssuesAcrossPages() {
        var shop = new Scope("SHOP",10L,7L,10L);
        db.update("UPDATE o_goods_inventory_batch SET current_qty=49 WHERE id=5");
        assertEquals("6", service.overview(1,1,shop).records().get(0).getSkuId());
        assertEquals("批次待核实", service.overview(1,1,shop).records().get(0).getRisk());
        assertEquals("批次待核实", service.productOverview(1,1,shop,null).records().get(0).getRisk());
        assertEquals(1L, service.riskSummary(shop).risks().get("批次待核实"));
        db.update("UPDATE o_goods_inventory_batch SET current_qty=50 WHERE id=5");
        db.update("UPDATE o_goods_inventory_batch SET warehouse_id=9 WHERE id=2");
        assertEquals("低库存", service.overview(1,50,shop).records().stream().filter(r -> r.getSkuId().equals("5")).findFirst().orElseThrow().getRisk());
        assertEquals("批次待核实", service.overview(1,50,new Scope("MERCHANT",7L,7L,null)).records().stream().filter(r -> r.getSkuId().equals("5")).findFirst().orElseThrow().getRisk());
    }
    @Test void skuSearchIsScopedAndTreatsWildcardsAsLiteral() {
        var shop = new Scope("SHOP",10L,7L,10L);
        var found = service.search("SKU-A",shop);
        assertEquals(1,found.size()); assertEquals("5",found.get(0).skuId());
        assertTrue(service.search("秘密",shop).isEmpty());
        assertEquals(1,service.search("秘密",new Scope("HEADQUARTERS",null,null,null)).size());
        assertTrue(service.search("%",shop).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.search(" ",shop));
        assertThrows(IllegalArgumentException.class, () -> service.search("SKU",new Scope("SUPPLIER",7L,null,null)));
    }
    @Test void productSearchRespectsScopeAndPagination() {
        var shop = new Scope("SHOP",10L,7L,10L);
        assertEquals(1,service.productOverview(1,10,shop,"SKU-A").total());
        assertEquals("5",service.productOverview(1,10,shop,"测试").records().get(0).getSkuId());
        assertEquals(0,service.productOverview(1,10,shop,"SECRET").total());
        assertEquals(0,service.productOverview(1,10,shop,"%").total());
        assertEquals(3,service.productOverview(1,1,shop,"").total());
        assertEquals(1,service.productOverview(2,1,shop,"").records().size());
        assertThrows(IllegalArgumentException.class,()->service.productOverview(1,10,shop,"a".repeat(101)));
    }
    @Test void scopesAndWarehouseAreEnforcedBySql() {
        assertEquals(3, service.find(5L,null,new Scope("HEADQUARTERS",null,null,null)).records().size());
        assertEquals(2, service.find(5L,null,new Scope("MERCHANT",7L,7L,null)).records().size());
        var shop = service.find(5L,1L,new Scope("SHOP",10L,7L,10L)).records();
        assertEquals(1, shop.size()); assertEquals(8, shop.get(0).getAvailableQuantity());
        assertTrue(service.find(5L,2L,new Scope("SHOP",10L,7L,10L)).records().isEmpty());
    }
    @Test void purchaseTasksEnforceScopeAndExcludeCompletedAndReturns() {
        assertEquals(3, service.purchaseInbound(5L,null,new Scope("HEADQUARTERS",null,null,null)).records().size());
        assertEquals(2, service.purchaseInbound(5L,null,new Scope("MERCHANT",7L,7L,null)).records().size());
        assertEquals(1, service.purchaseInbound(5L,1L,new Scope("SHOP",10L,7L,10L)).records().size());
        var task = service.purchaseInbound(5L,1L,new Scope("SHOP",10L,7L,10L)).records().get(0);
        var remaining = task.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("remainingQuantity")).findFirst().orElseThrow().getValue();
        assertEquals(6, ((Number) remaining).intValue());
        assertEquals(1, service.purchaseInbound(5L,1L,new Scope("SHOP",10L,7L,10L)).withoutTask().size());
        assertEquals(2, service.purchaseInbound(5L,null,new Scope("HEADQUARTERS",null,null,null)).withoutTask().size());
        assertTrue(service.purchaseInbound(5L,2L,new Scope("SHOP",10L,7L,10L)).records().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.purchaseInbound(5L,null,new Scope("SUPPLIER",1L,null,null)));
    }
    @Test void purchaseWithoutTaskChecksEachItemRatherThanOnlyTheOrder() {
        db.update("INSERT INTO erp_purchase_order_item VALUES (7,100,6,5,0,7,10)");
        var shop = new Scope("SHOP",10L,7L,10L);
        assertEquals(1, service.purchaseInbound(6L,null,shop).withoutTask().size());
        assertEquals(1, service.purchaseInbound(5L,null,shop).withoutTask().size());
        db.update("UPDATE erp_purchase_order SET status=3 WHERE id=100");
        assertEquals(1, service.purchaseInbound(6L,null,shop).withoutTask().size());
        assertNull(service.purchaseInbound(6L,null,shop).ledger().get(0).getRemainingQuantity());
    }
    @Test void purchaseLedgerUsesActualTasksAndRejectsDuplicateOrInconsistentReceipts() {
        var shop = new Scope("SHOP",10L,7L,10L);
        var rows = service.purchaseInbound(5L,null,shop).ledger();
        assertEquals(2, rows.size());
        var tasked = rows.stream().filter(r -> r.getOrderId().equals("100")).findFirst().orElseThrow();
        assertEquals(6L, tasked.getRemainingQuantity());
        assertNull(rows.stream().filter(r -> r.getOrderId().equals("200")).findFirst().orElseThrow().getRemainingQuantity());
        db.update("UPDATE erp_stock_in_item SET in_quantity=10,status=2 WHERE id=1");
        assertEquals(0L, service.purchaseInbound(5L,null,shop).ledger().stream().filter(r -> r.getOrderId().equals("100")).findFirst().orElseThrow().getRemainingQuantity());
        db.update("INSERT INTO erp_stock_in_item VALUES (7,1,'DUP',1,5,1,7,10,10,0,0,NULL,1)");
        assertNull(service.purchaseInbound(5L,null,shop).ledger().stream().filter(r -> r.getOrderId().equals("100")).findFirst().orElseThrow().getRemainingQuantity());
        assertEquals(3,service.purchaseInbound(5L,null,new Scope("HEADQUARTERS",null,null,null)).ledger().size());
    }
    @Test void shipmentSourcesRespectOrderAndSubjectWithoutDuplicatingSkuRows() {
        db.update("INSERT INTO erp_purchase_order_ship VALUES "
                + "(1,100,9,7,10,1,'Carrier','A',CURRENT_TIMESTAMP,NULL,0),"
                + "(2,200,9,7,10,2,'Carrier','B',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1),"
                + "(3,201,9,8,12,2,'Carrier','SECRET',CURRENT_TIMESTAMP,NULL,0),"
                + "(4,100,9,7,11,1,'Carrier','WRONG_SHOP',CURRENT_TIMESTAMP,NULL,0),"
                + "(5,100,8,7,10,1,'Carrier','WRONG_SUPPLIER',CURRENT_TIMESTAMP,NULL,0),"
                + "(6,202,9,7,10,1,'Carrier','CANCELLED',CURRENT_TIMESTAMP,NULL,0)");
        db.update("INSERT INTO erp_purchase_order_item VALUES (7,100,5,1,0,7,10)");
        var shop = new Scope("SHOP",10L,7L,10L);
        var rows = service.purchaseInbound(5L,null,shop).shipments();
        assertEquals(java.util.List.of("2","1"), rows.stream().map(r -> r.getId()).toList());
        assertNull(rows.get(1).getReceiptTime());
        assertNotNull(rows.get(0).getReceiptTime());
        assertEquals("1", service.purchaseInbound(5L,1L,shop).shipments().get(0).getId());
        assertEquals(1, service.purchaseInbound(5L,1L,shop).shipments().size());
        assertEquals(3, service.purchaseInbound(5L,null,new Scope("HEADQUARTERS",null,null,null)).shipments().size());
        assertEquals(2, service.purchaseInbound(5L,null,new Scope("MERCHANT",7L,7L,null)).shipments().size());
        assertTrue(service.purchaseInbound(6L,null,shop).shipments().isEmpty());
    }
    @Test void salesUsesCompletedDaysAndResolvedScope() {
        var shop = service.sales(5L,7,new Scope("SHOP",10L,7L,10L));
        assertEquals(2, shop.quantity());
        assertEquals(LocalDate.now().atStartOfDay(), shop.endExclusive());
        assertEquals(5, service.sales(5L,7,new Scope("MERCHANT",7L,7L,null)).quantity());
        assertEquals(9, service.sales(5L,7,new Scope("HEADQUARTERS",null,null,null)).quantity());
        assertThrows(IllegalArgumentException.class, () -> service.sales(5L,8,new Scope("HEADQUARTERS",null,null,null)));
    }
    @Test void invalidScopesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> service.find(5L,null,new Scope("SHOP",10L,null,10L)));
        assertEquals(2,service.find(5L,null,new Scope("WAREHOUSE",1L,null,null)).records().size());
        assertThrows(IllegalArgumentException.class, () -> service.find(5L,2L,new Scope("WAREHOUSE",1L,null,null)));
        assertThrows(IllegalArgumentException.class, () -> service.find(0L,null,new Scope("HEADQUARTERS",null,null,null)));
    }
    @Test void overviewScansScopedSkusIncludingUnknownInventoryAndPaginates() {
        var shop = new Scope("SHOP",10L,7L,10L);
        var first = service.overview(1,2,shop);
        assertEquals(3,first.total()); assertEquals(2,first.records().size());
        assertTrue(first.records().stream().anyMatch(r -> r.getSkuId().equals("5") && r.getRisk().equals("低库存")));
        assertTrue(first.records().stream().anyMatch(r -> r.getSkuId().equals("7") && r.getRisk().equals("待核实库存")));
        assertEquals(1,service.overview(2,2,shop).records().size());
        var headOffice = service.overview(1,50,new Scope("HEADQUARTERS",null,null,null));
        assertEquals("多仓待核实",headOffice.records().stream().filter(r -> r.getSkuId().equals("5")).findFirst().orElseThrow().getRisk());
        var warehouse = service.overview(1,50,new Scope("WAREHOUSE",1L,null,null));
        assertEquals(2,warehouse.total());
        assertTrue(warehouse.records().stream().noneMatch(r -> r.getSkuId().equals("7") || r.getSkuId().equals("8")));
        assertThrows(IllegalArgumentException.class,()->service.overview(1,50,new Scope("SUPPLIER",1L,null,null)));
    }
    @Test void duplicateOrInconsistentRowsAreNotPresentedAsReliableStock() {
        db.update("INSERT INTO o_goods_inventory(id,sku_id,warehouse_id,merchant_id,shop_id,quantity,locked_quantity,available_quantity,stock_status,is_delete) VALUES (8,5,1,7,10,2,0,2,1,0)");
        var row=service.overview(1,50,new Scope("SHOP",10L,7L,10L)).records().stream().filter(r->r.getSkuId().equals("5")).findFirst().orElseThrow();
        assertEquals("库存数据异常",row.getRisk());
    }
    @Test void overviewSalesRespectsScopeAndUnavailableIsDistinctFromZero() {
        var shop = new Scope("SHOP",10L,7L,10L);
        var rows = service.overview(1,50,shop,true).records();
        assertEquals(2L, rows.stream().filter(r -> r.getSkuId().equals("5")).findFirst().orElseThrow().getSales30Days());
        assertEquals(0L, rows.stream().filter(r -> r.getSkuId().equals("7")).findFirst().orElseThrow().getSales30Days());
        assertNull(service.overview(1,50,shop,false).records().get(0).getSales30Days());
        assertNull(service.overview(1,50,new Scope("WAREHOUSE",1L,null,null),true).records().get(0).getSales30Days());
        assertEquals(9L, service.overview(1,50,new Scope("HEADQUARTERS",null,null,null),true).records().stream().filter(r -> r.getSkuId().equals("5")).findFirst().orElseThrow().getSales30Days());
    }
    @Test void overviewFindsEmptyWarehouseScopeDespitePositiveAggregate() {
        db.update("UPDATE o_goods_inventory SET quantity=0,locked_quantity=0,available_quantity=0 WHERE id=3");
        db.update("UPDATE o_goods_inventory_batch SET current_qty=0 WHERE id=3");
        var hq = service.overview(1,50,new Scope("HEADQUARTERS",null,null,null)).records();
        assertEquals("部分范围缺货", hq.get(0).getRisk());
        assertEquals("5", hq.get(0).getSkuId());
        assertEquals(1, hq.get(0).getEmptyScopes().size());
        assertEquals("2", hq.get(0).getEmptyScopes().get(0).getWarehouseId());
        assertEquals("8", hq.get(0).getEmptyScopes().get(0).getMerchantId());
        var shop = service.overview(1,50,new Scope("SHOP",10L,7L,10L)).records();
        assertEquals("低库存", shop.stream().filter(r -> r.getSkuId().equals("5")).findFirst().orElseThrow().getRisk());
        assertTrue(shop.stream().allMatch(r -> r.getEmptyScopes().isEmpty()));
    }
    @Test void overviewPurchaseCountsAreScopedAndNotPresentedAsTransitQuantity() {
        var shop = new Scope("SHOP",10L,7L,10L);
        var result = service.overview(1,50,shop,false,true).records();
        assertEquals(2L, result.stream().filter(r -> r.getSkuId().equals("5")).findFirst().orElseThrow().getOpenPurchaseOrders());
        assertEquals(0L, result.stream().filter(r -> r.getSkuId().equals("7")).findFirst().orElseThrow().getOpenPurchaseOrders());
        assertNull(service.overview(1,50,shop,false,false).records().get(0).getOpenPurchaseOrders());
        assertNull(service.overview(1,50,new Scope("WAREHOUSE",1L,null,null),false,true).records().get(0).getOpenPurchaseOrders());
        assertEquals(3L, service.overview(1,50,new Scope("HEADQUARTERS",null,null,null),false,true).records().stream().filter(r -> r.getSkuId().equals("5")).findFirst().orElseThrow().getOpenPurchaseOrders());
    }
    @Test void riskSummaryRejectsOversizedCatalogInsteadOfReportingPartialTotals() {
        var mapper=org.mockito.Mockito.mock(AiInventoryMapper.class);
        org.mockito.Mockito.when(mapper.allOverview(null,null,null)).thenReturn(java.util.Collections.nCopies(20001,new cn.xinghuaerp.ai.vo.AiInventoryOverviewRow()));
        assertThrows(IllegalArgumentException.class, () -> new AiInventoryReadService(mapper).riskSummary(new Scope("HEADQUARTERS",null,null,null)));
    }
}
