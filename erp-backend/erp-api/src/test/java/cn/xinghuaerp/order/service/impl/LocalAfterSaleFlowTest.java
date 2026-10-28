package cn.xinghuaerp.order.service.impl;

import cn.xinghuaerp.inventory.bo.ShipStockUpCompleteBo;
import cn.xinghuaerp.inventory.request.StockInItem;
import cn.xinghuaerp.inventory.request.StockInLocalRequest;
import cn.xinghuaerp.inventory.request.StockInRequest;
import cn.xinghuaerp.inventory.request.StockOutItemRequest;
import cn.xinghuaerp.order.facade.OrderFacadeImpl;
import cn.xinghuaerp.inventory.facade.InventoryFacadeImpl;
import cn.xinghuaerp.system.facade.SystemFacadeImpl;
import cn.xinghuaerp.product.facade.ProductFacadeImpl;
import cn.xinghuaerp.purchase.facade.PurchaseFacadeImpl;
import cn.xinghuaerp.order.bo.OrderShipRequest;
import cn.xinghuaerp.order.request.LocalAfterSaleRequest;
import cn.xinghuaerp.order.bo.OrderItemShipperIdUpdateBo.Item;
import cn.xinghuaerp.inventory.mapper.OGoodsInventoryBatchMapper;
import cn.xinghuaerp.inventory.mapper.OGoodsInventoryMapper;
import cn.xinghuaerp.product.mapper.OGoodsSkuMapper;
import cn.xinghuaerp.inventory.mapper.ErpStockInItemMapper;
import cn.xinghuaerp.inventory.mapper.ErpStockInMapper;
import cn.xinghuaerp.inventory.mapper.ErpStockOutItemMapper;
import cn.xinghuaerp.inventory.mapper.ErpStockOutMapper;
import cn.xinghuaerp.inventory.mapper.ErpWarehouseMapper;
import cn.xinghuaerp.order.mapper.OOrderItemMapper;
import cn.xinghuaerp.order.mapper.OOrderMapper;
import cn.xinghuaerp.inventory.mapper.OOrderStockingItemBatchMapper;
import cn.xinghuaerp.inventory.mapper.OOrderStockingItemMapper;
import cn.xinghuaerp.inventory.mapper.OOrderStockingMapper;
import cn.xinghuaerp.order.mapper.ORefundAfterSaleMapper;
import cn.xinghuaerp.order.mapper.ORefundMapper;
import cn.xinghuaerp.order.mapper.ShopOrderMapper;
import cn.xinghuaerp.order.mapper.ShopRefundMapper;
import cn.xinghuaerp.system.mapper.ErpLogisticsCompanyMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.product.mapper.ErpSupplierProductItemMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierProductMapper;
import cn.xinghuaerp.order.bo.OrderShipRequest;
import cn.xinghuaerp.inventory.bo.ShipStockUpCompleteBo;
import cn.xinghuaerp.order.request.LocalAfterSaleRequest;
import cn.xinghuaerp.inventory.request.StockInItem;
import cn.xinghuaerp.inventory.request.StockInLocalRequest;
import cn.xinghuaerp.inventory.request.StockInRequest;
import cn.xinghuaerp.inventory.request.StockOutItemRequest;
import cn.xinghuaerp.inventory.service.impl.OGoodsInventoryBatchServiceImpl;
import cn.xinghuaerp.inventory.service.impl.OGoodsInventoryServiceImpl;
import cn.xinghuaerp.product.service.impl.OGoodsSkuServiceImpl;
import cn.xinghuaerp.inventory.service.impl.ErpStockInItemServiceImpl;
import cn.xinghuaerp.inventory.service.impl.ErpStockInServiceImpl;
import cn.xinghuaerp.inventory.service.impl.ErpStockOutItemServiceImpl;
import cn.xinghuaerp.inventory.service.impl.ErpStockOutServiceImpl;
import cn.xinghuaerp.order.service.LocalAfterSaleService;
import cn.xinghuaerp.system.service.SalesScope;
import cn.xinghuaerp.inventory.service.impl.OOrderStockingItemBatchServiceImpl;
import cn.xinghuaerp.order.service.impl.OOrderStockingItemServiceImpl;
import cn.xinghuaerp.fulfillment.service.ManualShipmentService;
import cn.xinghuaerp.utils.UUID;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
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

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end warehouse mutations with real MyBatis mappers/transactions in an isolated H2 database. */
class LocalAfterSaleFlowTest {
    private JdbcTemplate db;
    private SqlSessionTemplate session;
    private DataSourceTransactionManager transactions;
    private ManualShipmentService shipment;
    private OOrderStockingItemServiceImpl stocking;
    private ErpStockOutServiceImpl outbound;
    private ErpStockInServiceImpl inbound;
    private LocalAfterSaleService after;
    private final SalesScope admin = new SalesScope(null, null);

    @BeforeEach
    void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        new ResourceDatabasePopulator(new ClassPathResource("sales-shipping-schema.sql"), new ClassPathResource("local-after-sale-schema.sql"), new ClassPathResource("supplier-dispatch-schema.sql")).execute(ds);
        db = new JdbcTemplate(ds); transactions = new DataSourceTransactionManager(ds);
        var config = new MybatisConfiguration();
        for (var mapper : List.of(OOrderMapper.class, OOrderItemMapper.class, OOrderStockingMapper.class, OOrderStockingItemMapper.class,
                ErpWarehouseMapper.class, ErpLogisticsCompanyMapper.class, OGoodsSkuMapper.class, ErpStockOutMapper.class, ErpStockOutItemMapper.class,
                OGoodsInventoryMapper.class, OGoodsInventoryBatchMapper.class, OOrderStockingItemBatchMapper.class, ShopRefundMapper.class, ShopOrderMapper.class,
                ORefundMapper.class, ORefundAfterSaleMapper.class, ErpStockInMapper.class, ErpStockInItemMapper.class, ErpSupplierMapper.class, ErpSupplierProductMapper.class, ErpSupplierProductItemMapper.class)) config.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        session = new SqlSessionTemplate(factory.getObject());
        var orderFacade = new OrderFacadeImpl(null, null, map(OOrderItemMapper.class), map(OOrderMapper.class), null, map(ShopOrderMapper.class));
        var inventoryFacade = new InventoryFacadeImpl(map(ErpStockInItemMapper.class), map(ErpStockInMapper.class), map(ErpStockOutItemMapper.class), map(ErpStockOutMapper.class), map(ErpWarehouseMapper.class), map(OOrderStockingItemMapper.class), map(OOrderStockingMapper.class));
        var purchaseFacade = new PurchaseFacadeImpl(null, map(ErpSupplierMapper.class), map(ErpSupplierProductMapper.class));
        var productFacade = new ProductFacadeImpl(map(ErpSupplierProductItemMapper.class), null, map(OGoodsSkuMapper.class), null, null);
        var systemFacade = new SystemFacadeImpl(map(ErpLogisticsCompanyMapper.class), null, null);
        shipment = tx(new ManualShipmentService(orderFacade, orderFacade, inventoryFacade, inventoryFacade, inventoryFacade, systemFacade, productFacade));
        stocking = tx(new OOrderStockingItemServiceImpl(inventoryFacade, inventoryFacade, inventoryFacade, inventoryFacade, inventoryFacade));
        var inventory = inject(new OGoodsInventoryServiceImpl(), map(OGoodsInventoryMapper.class));
        var batches = inject(new OGoodsInventoryBatchServiceImpl(), map(OGoodsInventoryBatchMapper.class));
        outbound = tx(new ErpStockOutServiceImpl(map(ErpStockOutMapper.class), inject(new ErpStockOutItemServiceImpl(map(ErpStockOutItemMapper.class)), map(ErpStockOutItemMapper.class)), null,
                inject(new OOrderStockingItemBatchServiceImpl(), map(OOrderStockingItemBatchMapper.class)), stocking, map(OOrderStockingMapper.class), inventory, batches));
        inbound = tx(new ErpStockInServiceImpl(map(ErpStockInMapper.class), inject(new ErpStockInItemServiceImpl(), map(ErpStockInItemMapper.class)),
                new cn.xinghuaerp.product.facade.OGoodsSkuFacadeImpl(inject(new OGoodsSkuServiceImpl(map(OGoodsSkuMapper.class)), map(OGoodsSkuMapper.class))), null, null, inventory, batches));
        after = tx(new LocalAfterSaleService(map(OOrderMapper.class), map(OOrderItemMapper.class), productFacade, map(ShopRefundMapper.class), map(ORefundMapper.class), map(ORefundAfterSaleMapper.class), inventoryFacade, systemFacade, inventoryFacade, inventoryFacade, inventoryFacade, inventoryFacade, inventoryFacade, inventoryFacade, map(ShopOrderMapper.class), purchaseFacade, purchaseFacade, productFacade));
        db.update("INSERT INTO erp_warehouse(id,warehouse_type,warehouse_no,warehouse_name,status,merchant_id,warehouse_source) VALUES(1,'LOCAL','MAIN','主仓',1,0,0)");
        db.update("INSERT INTO erp_logistics_company(id,name,code,status) VALUES(1,'测试快递','TEST',1)");
        db.update("INSERT INTO o_goods_sku(id,goods_id,sku_code,goods_name,inventory_mode,ship_type,merchant_id) VALUES(101,201,'SKU-1','电饭煲',0,0,0),(102,202,'SKU-2','替换商品',0,0,0)");
        db.update("INSERT INTO o_order(id,order_mode,order_num,shop_type,shop_id,merchant_id,order_status,amount) VALUES(1,1,'SALE-001',999,10,0,1,100)");
        db.update("INSERT INTO o_order_item(id,order_id,order_num,sub_order_num,goods_id,goods_sku_id,goods_title,goods_price,payment,item_amount,quantity,refund_status,refund_count,sku_id,shop_type,shop_id) VALUES(1,1,'SALE-001','SALE-001-1',201,101,'电饭煲',10,100,100,10,1,0,'SHOPSKU-1',999,10)");
        db.update("INSERT INTO o_goods_inventory(id,goods_id,sku_id,quantity,available_quantity,warehouse_id) VALUES(1,201,101,100,100,1),(2,202,102,50,50,1)");
        db.update("INSERT INTO o_goods_inventory_batch(id,inventory_id,batch_num,goods_id,sku_id,origin_qty,current_qty,warehouse_id) VALUES(1,1,'BATCH-1',201,101,100,100,1),(2,2,'BATCH-2',202,102,50,50,1)");
    }
    @AfterEach void shutdown() { db.execute("SHUTDOWN"); }

    @Test void returnedGoodsOnlyIncreaseStockAtActualInboundThenCanComplete() {
        sell(); String num = create(10, 2, "20"); after.process(num, admin, "tester");
        assertStock(101, 90); after.confirm(tasks(num), admin, "tester"); assertStock(101, 90);
        assertThrows(IllegalArgumentException.class, () -> after.complete(tasks(num), admin, "tester"));
        receive(2); assertStock(101, 92);
        var complete = tasks(num); complete.setRefundPaid(true); complete.setRemark("线下退款凭据 TEST-001");
        after.complete(complete, admin, "tester");
        assertEquals(4, value("SELECT status FROM o_refund"));
        assertEquals(10, value("SELECT status FROM o_refund_after_sale"));
        assertThrows(IllegalArgumentException.class, () -> after.complete(complete, admin, "tester")); assertStock(101, 92);
    }
    @Test void exchangeReturnsOriginalSkuAndDeductsSelectedReplacement() {
        sell(); String num = create(20, 2, "0"); after.process(num, admin, "tester"); after.confirm(tasks(num), admin, "tester");
        receive(2); assertStock(101, 92); assertStock(102, 50);
        long id = db.queryForObject("SELECT exchange_erp_order_id FROM o_refund_after_sale", Long.class);
        generateAndOut(id, 2); assertStock(101, 92); assertStock(102, 48);
        after.complete(tasks(num), admin, "tester"); assertEquals(32, value("SELECT erp_status FROM o_refund"));
    }
    @Test void reissueOnlyDeductsStockAndDoesNotCreateAnotherSalesOrder() {
        sell(); String num = create(80, 1, "0"); after.process(num, admin, "tester"); after.confirm(tasks(num), admin, "tester");
        assertEquals(0, value("SELECT COUNT(*) FROM erp_stock_in")); assertStock(101, 90);
        long id = db.queryForObject("SELECT exchange_erp_order_id FROM o_refund_after_sale", Long.class);
        generateAndOut(id, 1); assertStock(101, 89); after.complete(tasks(num), admin, "tester");
        assertEquals(1, value("SELECT COUNT(*) FROM o_order"));
    }
    @Test void refundOnlyRequiresSettlementEvidenceAndNeverAddsInventory() {
        sell(); String num = create(11, 1, "5"); after.process(num, admin, "tester");
        assertThrows(IllegalArgumentException.class, () -> after.complete(tasks(num), admin, "tester"));
        var request = tasks(num); request.setRefundPaid(true); request.setRemark("已退差价，凭据 TEST-2"); after.complete(request, admin, "tester");
        assertStock(101, 90); assertEquals(0, value("SELECT COUNT(*) FROM erp_stock_in"));
    }
    private void cancelledVendorTask() {
        db.update("INSERT INTO o_order_stocking(id,shipper_id,o_order_id,order_num,shop_type,shop_id,merchant_id,type,order_type,order_status,send_status) VALUES(999,1,1,'SALE-001',999,10,0,300,0,11,-1)");
        db.update("INSERT INTO o_order_stocking_item(id,ship_order_id,o_order_id,o_order_item_id,order_num,supplier_id,sku_id,quantity,merchant_id,unshipped_quantity) VALUES(999,999,1,1,'SALE-001',1,'SHOPSKU-1',10,0,10)");
    }
    @Test void cancelledVendorTaskDoesNotBlockPresaleRefund() {
        cancelledVendorTask(); String num=create(1,10,"100"); after.process(num,admin,"tester"); assertNotNull(num);
    }
    @Test void cancelledVendorTaskDoesNotContaminateLocalShipmentRefund() {
        cancelledVendorTask(); sell(); String num=create(10,2,"20"); after.process(num,admin,"tester");
        assertEquals(1,value("SELECT send_warehouse_id FROM o_refund_after_sale WHERE refund_num='"+num+"'"));
    }
    @Test void preSaleRefundStopsShipmentWithoutAddingStock() {
        db.update("INSERT INTO oms_shop_order(id,merchant_id,shop_id,order_id,order_status,goods_amount,order_amount,payment_amount,order_mode) VALUES(1,0,10,'SALE-001',1,10000,10000,10000,1)");
        String num = create(1, 10, "100"); after.process(num, admin, "tester");
        assertEquals(12,value("SELECT order_status FROM oms_shop_order"));
        assertNotEquals(0, shipment.ship(shipRequest(), "tester").getCode());
        var request = tasks(num); request.setRefundPaid(true); request.setRemark("售前退款凭据 TEST-3"); after.complete(request, admin, "tester");
        assertEquals(11, value("SELECT order_status FROM o_order")); assertEquals(11,value("SELECT order_status FROM oms_shop_order")); assertStock(101, 100);
    }
    @Test void validatesOwnershipCumulativeQuantityAndAmountsBeforeWriting() {
        sell(); var request = request(10, 2, "20");
        assertThrows(IllegalArgumentException.class, () -> after.create(request, new SalesScope(null, 999L), "tester"));
        request.setRefundAmount(new BigDecimal("101")); assertThrows(IllegalArgumentException.class, () -> after.create(request, admin, "tester"));
        assertEquals(0, value("SELECT COUNT(*) FROM o_refund"));
        create(10, 8, "80"); assertThrows(IllegalArgumentException.class, () -> create(10, 3, "20"));
        assertEquals(1, value("SELECT COUNT(*) FROM o_refund"));
    }
    @Test void duplicateAndConcurrentConfirmOnlyCreateOneWarehouseTask() throws Exception {
        sell(); String num = create(10, 2, "20"); after.process(num, admin, "tester");
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Boolean> action = () -> { start.await(); try { after.confirm(tasks(num), admin, "tester"); return true; } catch (IllegalArgumentException expected) { return false; } };
            var a = executor.submit(action); var b = executor.submit(action); start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, value("SELECT COUNT(*) FROM erp_stock_in")); assertStock(101, 90);
        } finally { executor.shutdownNow(); }
    }
    @Test void confirmWriteFailureRollsBackAllGeneratedTasksAndCanRetry() {
        sell(); String num = create(20, 2, "0"); after.process(num, admin, "tester");
        db.execute("ALTER TABLE o_order_stocking_item ADD CONSTRAINT force_after_failure CHECK(quantity <> 2)");
        assertThrows(RuntimeException.class, () -> after.confirm(tasks(num), admin, "tester"));
        assertEquals(0, value("SELECT COUNT(*) FROM erp_stock_in"));
        assertEquals(0, value("SELECT status FROM o_refund_after_sale"));
        db.execute("ALTER TABLE o_order_stocking_item DROP CONSTRAINT force_after_failure");
        after.confirm(tasks(num), admin, "tester"); assertEquals(1, value("SELECT COUNT(*) FROM erp_stock_in"));
    }
    @Test void inboundRejectsOverReceiptAndRepeatsWithoutInventoryChanges() {
        sell(); String num = create(10, 2, "20"); after.process(num, admin, "tester"); after.confirm(tasks(num), admin, "tester");
        assertNotEquals(0, inbound.localStockIn(inRequest(3), 1L, "tester").getCode()); assertStock(101, 90);
        receive(2); assertNotEquals(0, inbound.localStockIn(inRequest(2), 1L, "tester").getCode()); assertStock(101, 92);
    }

    @Test void cancellingUnconfirmedApplicationReleasesQuotaButCannotCancelWarehouseTasks() {
        sell(); String num = create(10, 10, "100"); after.process(num, admin, "tester"); after.cancel(num, admin, "tester");
        assertStock(101,90); assertEquals(1, value("SELECT status FROM o_refund"));
        String next = create(10, 2, "20"); after.process(next, admin, "tester"); after.confirm(tasks(next), admin, "tester");
        assertThrows(IllegalArgumentException.class, () -> after.cancel(next, admin, "tester"));
        assertThrows(IllegalArgumentException.class, () -> after.process(num, admin, "tester"));
    }

    @Test void inboundIgnoresInventoryFromAnotherMerchantAndShop() {
        sell(); String num=create(10,2,"20"); after.process(num,admin,"tester"); after.confirm(tasks(num),admin,"tester");
        db.update("INSERT INTO o_goods_inventory(id,goods_id,sku_id,quantity,available_quantity,warehouse_id,merchant_id,shop_id) VALUES(20,201,101,50,50,1,9,10),(21,201,101,60,60,1,0,11)");
        receive(2);
        assertEquals(92,db.queryForObject("SELECT quantity FROM o_goods_inventory WHERE id=1",Integer.class));
        assertEquals(50,db.queryForObject("SELECT quantity FROM o_goods_inventory WHERE id=20",Integer.class));
        assertEquals(60,db.queryForObject("SELECT quantity FROM o_goods_inventory WHERE id=21",Integer.class));
    }
    @Test void inboundRejectsAmbiguousSharedAndShopInventoryAndRollsBack() {
        sell(); String num=create(10,2,"20"); after.process(num,admin,"tester"); after.confirm(tasks(num),admin,"tester");
        db.update("INSERT INTO o_goods_inventory(id,goods_id,sku_id,quantity,available_quantity,warehouse_id,merchant_id,shop_id) VALUES(20,201,101,50,50,1,0,10)");
        assertThrows(IllegalStateException.class, () -> receive(2));
        assertEquals(90,db.queryForObject("SELECT quantity FROM o_goods_inventory WHERE id=1",Integer.class));
        assertEquals(50,db.queryForObject("SELECT quantity FROM o_goods_inventory WHERE id=20",Integer.class));
        assertEquals(0,value("SELECT in_quantity FROM erp_stock_in_item"));
    }

    @Test void inboundRejectsDetailWithDifferentWarehouse() {
        sell(); String num=create(10,2,"20"); after.process(num,admin,"tester"); after.confirm(tasks(num),admin,"tester");
        db.update("UPDATE erp_stock_in_item SET warehouse_id=2");
        assertNotEquals(0,inbound.localStockIn(inRequest(2),1L,"tester").getCode());
        assertStock(101,90);
    }

    @Test void inboundFailureRollsBackTotalStockAndAllowsRetry() {
        sell(); String num=create(10,2,"20"); after.process(num,admin,"tester"); after.confirm(tasks(num),admin,"tester");
        db.execute("ALTER TABLE o_goods_inventory_batch ADD CONSTRAINT reject_return CHECK(origin_qty <> 2)");
        assertThrows(RuntimeException.class, () -> receive(2)); assertStock(101,90);
        assertEquals(0,value("SELECT in_quantity FROM erp_stock_in_item"));
        db.execute("ALTER TABLE o_goods_inventory_batch DROP CONSTRAINT reject_return"); receive(2); assertStock(101,92);
    }

    @Test void concurrentActualInboundOnlyAddsReturnedGoodsOnce() throws Exception {
        sell(); String num=create(10,2,"20"); after.process(num,admin,"tester"); after.confirm(tasks(num),admin,"tester");
        var request=inRequest(2); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Integer> action=()->{start.await();return inbound.localStockIn(request,1L,"tester").getCode();};
            var a=pool.submit(action);var b=pool.submit(action);start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS)==0,b.get(10,TimeUnit.SECONDS)==0); assertStock(101,92);
        } finally {pool.shutdownNow();}
    }

    @Test void refusesReceiptRowsFromAnotherEntryWithoutPartialMutation() {
        sell(); String num=create(10,2,"20");after.process(num,admin,"tester");after.confirm(tasks(num),admin,"tester");
        var request=inRequest(1);request.setId(999L);assertNotEquals(0,inbound.localStockIn(request,1L,"tester").getCode());assertStock(101,90);
        var repeated=inRequest(1);repeated.setItems(List.of(repeated.getItems().get(0),repeated.getItems().get(0)));
        assertNotEquals(0,inbound.localStockIn(repeated,1L,"tester").getCode());assertStock(101,90);
    }

    @Test void rejectsAfterSaleBeforeActualStockOutAndSerialModeWithoutWriting() {
        assertThrows(IllegalArgumentException.class,()->create(10,1,"10"));
        sell();db.update("UPDATE o_goods_sku SET inventory_mode=1 WHERE id=101");
        assertThrows(IllegalArgumentException.class,()->create(10,1,"10"));
        assertEquals(0,value("SELECT COUNT(*) FROM o_refund"));assertStock(101,90);
    }

    private void legacyPurchaseEntries() {
        db.update("INSERT INTO erp_stock_in(id,stock_in_num,stock_in_type,source_no,merchant_id,shop_id,warehouse_id,status) VALUES(700,'SI-700',1,'PUR-700',0,0,1,0),(701,'SI-701',1,'PUR-701',0,0,1,0)");
        db.update("INSERT INTO erp_stock_in_item(id,stock_in_id,stock_in_type,source_item_id,goods_id,sku_id,quantity,pur_price,in_quantity,warehouse_id,merchant_id,shop_id,inventory_mode,status) VALUES(700,700,1,1,201,101,10,1,0,1,0,0,0,0),(701,701,1,2,201,101,10,1,0,1,0,0,0,0)");
    }
    private StockInRequest legacyRequest(int quantity) {
        var item=new StockInItem(); item.setId(700L); item.setIntoQuantity(quantity);
        var request=new StockInRequest(); request.setStockInId(700L); request.setWarehouseId(1L); request.setItemList(List.of(item)); return request;
    }
    @Test void legacyPurchaseInboundIsPartialAndRejectsExcess() {
        legacyPurchaseEntries();
        assertEquals(0,inbound.stockIn(1L,"tester",legacyRequest(4)).getCode()); assertStock(101,104);
        assertEquals(1,value("SELECT status FROM erp_stock_in_item WHERE id=700"));
        assertNotEquals(0,inbound.stockIn(1L,"tester",legacyRequest(7)).getCode()); assertStock(101,104);
        assertEquals(0,inbound.stockIn(1L,"tester",legacyRequest(6)).getCode()); assertStock(101,110);
        assertEquals(2,value("SELECT status FROM erp_stock_in_item WHERE id=700"));
    }
    @Test void concurrentLegacyPurchaseInboundCannotExceedRemainingQuantity() throws Exception {
        legacyPurchaseEntries(); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Integer> action=()->{ start.await(); return inbound.stockIn(1L,"tester",legacyRequest(6)).getCode(); };
            var a=pool.submit(action); var b=pool.submit(action); start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS)==0,b.get(10,TimeUnit.SECONDS)==0);
            assertStock(101,106); assertEquals(6,value("SELECT in_quantity FROM erp_stock_in_item WHERE id=700"));
        } finally { pool.shutdownNow(); }
    }

    @Test void legacyPurchaseInboundRejectsMixedEntryBeforeAnyWrite() {
        legacyPurchaseEntries(); var request=legacyRequest(2); var foreign=new StockInItem(); foreign.setId(701L); foreign.setIntoQuantity(1);
        request.setItemList(List.of(request.getItemList().get(0),foreign));
        assertNotEquals(0,inbound.stockIn(1L,"tester",request).getCode()); assertStock(101,100);
        assertEquals(0,value("SELECT in_quantity FROM erp_stock_in_item WHERE id=700"));
    }

    @Test void legacyInboundCannotBypassLocalReturnValidation() {
        sell();String num=create(10,2,"20");after.process(num,admin,"tester");after.confirm(tasks(num),admin,"tester");
        var local=inRequest(2);var legacy=new StockInRequest();legacy.setStockInId(local.getId());legacy.setWarehouseId(1L);
        legacy.setItemList(List.of(new StockInItem()));
        assertNotEquals(0,inbound.stockIn(1L,"tester",legacy).getCode());assertStock(101,90);
    }

    @Test void mixedEntryReceiptIsRejectedBeforeAnyRowChangesStock() {
        sell();String num=create(10,2,"20");after.process(num,admin,"tester");after.confirm(tasks(num),admin,"tester");
        var request=inRequest(1);var first=request.getItems().get(0);
        var foreign=map(ErpStockInItemMapper.class).selectById(first.getItemId());foreign.setId(null);foreign.setStockInId(999L);map(ErpStockInItemMapper.class).insert(foreign);
        var wrong=new StockInLocalRequest.Item();wrong.setItemId(foreign.getId());wrong.setQuantity(1);request.setItems(List.of(first,wrong));
        assertNotEquals(0,inbound.localStockIn(request,1L,"tester").getCode());assertStock(101,90);
        assertEquals(0,value("SELECT SUM(in_quantity) FROM erp_stock_in_item"));
    }

    @Test void concurrentApplicationsCannotExceedSoldQuantity() throws Exception {
        sell();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> action=()->{start.await();try{after.create(request(10,6,"60"),admin,"tester");return true;}catch(IllegalArgumentException expected){return false;}};
            var a=pool.submit(action);var b=pool.submit(action);start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(1,value("SELECT COUNT(*) FROM o_refund"));assertStock(101,90);
        }finally{pool.shutdownNow();}
    }

    private void vendorShipment(int count) {
        db.update("INSERT INTO erp_supplier(id,name,number,is_shipper,disable,is_delete,merchant_id) VALUES(7,'供应商','SUP-7',1,0,0,0)");
        db.update("INSERT INTO erp_supplier_product(id,supplier_id,erp_goods_id,status) VALUES(1,7,201,1),(2,7,202,1)");
        db.update("INSERT INTO erp_supplier_product_item(id,supplier_product_id,supplier_id,erp_goods_id,erp_goods_sku_id,status) VALUES(1,1,7,201,101,1),(2,2,7,202,102,1)");
        db.update("INSERT INTO o_order_stocking(id,shipper_id,o_order_id,order_num,shop_type,shop_id,merchant_id,type,order_type,order_status,send_status) VALUES(999,7,1,'SALE-001',999,10,0,300,0,2,2)");
        db.update("INSERT INTO o_order_stocking_item(id,ship_order_id,o_order_id,o_order_item_id,order_num,supplier_id,sku_id,quantity,merchant_id,unshipped_quantity,send_status) VALUES(999,999,1,1,'SALE-001',7,'SHOPSKU-1',?,0,0,2)",count);
        db.update("UPDATE o_order SET order_status=2,ship_status=2 WHERE id=1");
        db.update("UPDATE o_order_item SET ship_status=2,has_push_erp=1 WHERE id=1");
    }
    private void mixedShipment() {
        var r=shipment.ship(shipRequest(),"tester"); assertEquals(0,r.getCode());
        db.update("UPDATE o_order_stocking_item SET quantity=6 WHERE ship_order_id=?",r.getData());
        generateAndOut(r.getData(),6); vendorShipment(4); assertStock(101,94);
    }
    @Test void supplierRefundOnlyDoesNotChangeInventory() {
        vendorShipment(10); String num=create(11,3,"30"); after.process(num,admin,"tester");
        var r=tasks(num); assertThrows(IllegalArgumentException.class,()->after.complete(r,admin,"tester"));
        r.setRefundPaid(true); r.setRemark("已线下退款 TEST"); after.complete(r,admin,"tester");
        assertStock(101,100); assertEquals(0,value("SELECT COUNT(*) FROM erp_stock_in"));
    }
    @Test void supplierReturnRequiresReceiptAndNeverIncreasesLocalStock() {
        vendorShipment(10); String num=create(10,3,"30"); after.process(num,admin,"tester");
        var r=tasks(num); r.setReturnType(300); r.setReturnSupplierId(7L); after.confirm(r,admin,"tester");
        assertEquals(0,value("SELECT COUNT(*) FROM erp_stock_in")); assertStock(101,100);
        r.setRefundPaid(true); r.setRemark("对方收货并退款凭据");
        assertThrows(IllegalArgumentException.class,()->after.complete(r,admin,"tester"));
        r.setReturnReceived(true); after.complete(r,admin,"tester"); assertStock(101,100);
        assertEquals(300,value("SELECT return_type FROM o_refund_after_sale"));
    }
    @Test void mixedReturnToLocalWarehouseRequiresActualInbound() {
        mixedShipment(); String num=create(10,4,"40"); after.process(num,admin,"tester"); var r=tasks(num); after.confirm(r,admin,"tester");
        r.setRefundPaid(true); r.setRemark("退款凭据"); assertThrows(IllegalArgumentException.class,()->after.complete(r,admin,"tester"));
        receive(4); after.complete(r,admin,"tester"); assertStock(101,98);
    }
    @Test void supplierReturnCannotExceedItsShippedQuantity() {
        mixedShipment(); String num=create(10,5,"50"); after.process(num,admin,"tester");
        var r=tasks(num); r.setReturnType(300); r.setReturnSupplierId(7L);
        assertThrows(IllegalArgumentException.class,()->after.confirm(r,admin,"tester"));
        r.setReturnSupplierId(99L); assertThrows(IllegalArgumentException.class,()->after.confirm(r,admin,"tester")); assertStock(101,94);
    }
    @Test void mixedSupplierReturnsSerializeSupplierQuantity() throws Exception {
        mixedShipment(); String a=create(10,3,"30"), b=create(10,3,"30"); after.process(a,admin,"tester"); after.process(b,admin,"tester");
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            java.util.function.Function<String,Callable<Boolean>> task=num->()->{start.await();var r=tasks(num);r.setReturnType(300);r.setReturnSupplierId(7L);try{after.confirm(r,admin,"tester");return true;}catch(IllegalArgumentException e){return false;}};
            var first=pool.submit(task.apply(a));var second=pool.submit(task.apply(b));start.countDown();
            assertNotEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)); assertStock(101,94);
        } finally {pool.shutdownNow();}
    }
    @Test void completedSupplierReturnConsumesSupplierCapacity() {
        mixedShipment(); String first=create(10,3,"30"); after.process(first,admin,"tester");
        var a=tasks(first); a.setReturnType(300); a.setReturnSupplierId(7L); after.confirm(a,admin,"tester");
        a.setReturnReceived(true); a.setRefundPaid(true); a.setRemark("已核对供应商收货和退款"); after.complete(a,admin,"tester");
        String second=create(10,2,"20"); after.process(second,admin,"tester"); var b=tasks(second); b.setReturnType(300); b.setReturnSupplierId(7L);
        assertThrows(IllegalArgumentException.class,()->after.confirm(b,admin,"tester")); assertStock(101,94);
    }
    @Test void supplierAfterSaleIsRestrictedAndCannotExchange() {
        vendorShipment(10); var r=request(11,1,"10");
        assertThrows(IllegalArgumentException.class,()->after.create(r,new SalesScope(0L,null),"tester"));
        String num=create(11,1,"10");
        assertThrows(IllegalArgumentException.class,()->after.process(num,new SalesScope(0L,null),"tester"));
        assertThrows(IllegalArgumentException.class,()->after.create(request(20,1,"0"),new SalesScope(0L,null),"tester"));
        db.update("UPDATE o_order_stocking SET send_status=0 WHERE id=999");
        assertThrows(IllegalArgumentException.class,()->create(11,1,"10"));
    }
    @Test void supplierReissueRequiresActualSupplierShipmentWithoutLocalStockChange() {
        vendorShipment(10); String num=create(80,2,"0"); after.process(num,admin,"tester");
        var r=tasks(num); r.setReissueType(300); r.setReissueSupplierId(7L); after.confirm(r,admin,"tester");
        assertStock(101,100); assertEquals(0,value("SELECT COUNT(*) FROM erp_stock_out"));
        assertThrows(IllegalArgumentException.class,()->after.complete(r,admin,"tester"));
        after.confirmVendorReissue(r,admin,"tester"); assertThrows(IllegalArgumentException.class,()->after.confirmVendorReissue(r,admin,"tester"));
        after.complete(r,admin,"tester"); assertStock(101,100); assertEquals(2,value("SELECT order_status FROM o_order WHERE id=1"));
    }
    @Test void vendorExchangeReturnAndReplacementNeedSeparateConfirmations() {
        vendorShipment(10); String num=create(20,2,"0"); after.process(num,admin,"tester");
        var r=tasks(num); r.setReturnType(300); r.setReturnSupplierId(7L); r.setReissueType(300); r.setReissueSupplierId(7L); after.confirm(r,admin,"tester");
        assertThrows(IllegalArgumentException.class,()->after.complete(r,admin,"tester"));
        r.setReturnReceived(true); r.setRemark("供应商确认收到退货"); assertThrows(IllegalArgumentException.class,()->after.complete(r,admin,"tester"));
        after.confirmVendorReissue(r,admin,"tester"); after.complete(r,admin,"tester");
        assertEquals(0,value("SELECT COUNT(*) FROM erp_stock_in")); assertEquals(0,value("SELECT COUNT(*) FROM erp_stock_out")); assertStock(101,100); assertStock(102,50);
    }
    @Test void concurrentVendorReissueConfirmationIsIdempotent() throws Exception {
        vendorShipment(10); String num=create(80,2,"0"); after.process(num,admin,"tester");
        var r=tasks(num); r.setReissueType(300); r.setReissueSupplierId(7L); after.confirm(r,admin,"tester");
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Boolean> call=()->{start.await();try {after.confirmVendorReissue(r,admin,"tester");return true;}catch(IllegalArgumentException e){return false;}};
            var a=pool.submit(call); var b=pool.submit(call); start.countDown(); assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(1,value("SELECT COUNT(*) FROM o_order_stocking WHERE order_type=80 AND send_status=2")); assertStock(101,100);
        } finally {pool.shutdownNow();}
    }
    @Test void vendorReissueRejectsAlteredSkuAndRollsBack() {
        vendorShipment(10); String num=create(80,2,"0"); after.process(num,admin,"tester");
        var r=tasks(num); r.setReissueType(300); r.setReissueSupplierId(7L); after.confirm(r,admin,"tester");
        db.update("UPDATE o_order_stocking_item SET goods_sku_id=102 WHERE order_num=?",num);
        assertThrows(IllegalArgumentException.class,()->after.confirmVendorReissue(r,admin,"tester"));
        assertEquals(0,value("SELECT send_status FROM o_order_stocking WHERE order_type=80")); assertStock(101,100);
    }
    @Test void vendorOriginCanExchangeViaLocalWarehouse() {
        vendorShipment(10); String num=create(20,2,"0"); after.process(num,admin,"tester"); var r=tasks(num); after.confirm(r,admin,"tester");
        receive(2); long id=db.queryForObject("SELECT exchange_erp_order_id FROM o_refund_after_sale",Long.class); generateAndOut(id,2); after.complete(r,admin,"tester");
        assertStock(101,102); assertStock(102,48);
    }
    @Test void vendorReplacementMustHaveAuthorizedActiveOffer() {
        vendorShipment(10); String num=create(80,1,"0"); after.process(num,admin,"tester"); var r=tasks(num); r.setReissueType(300); r.setReissueSupplierId(7L);
        db.update("UPDATE erp_supplier_product SET status=2 WHERE id=1"); assertThrows(IllegalArgumentException.class,()->after.confirm(r,admin,"tester"));
        assertEquals(1,value("SELECT COUNT(*) FROM o_order_stocking")); assertEquals(0,value("SELECT status FROM o_refund_after_sale"));
    }
    private String create(int type, int qty, String amount) { after.create(request(type, qty, amount), admin, "tester"); return db.queryForObject("SELECT refund_num FROM o_refund ORDER BY id DESC LIMIT 1", String.class); }
    private LocalAfterSaleRequest request(int type, int qty, String amount) { var r = new LocalAfterSaleRequest(); r.setShopId(10L); r.setOrderItemId(1L); r.setType(type); r.setQuantity(qty); r.setRefundAmount(new BigDecimal(amount)); r.setReason("本地隔离测试"); if (type == 20) r.setExchangeSkuId(102L); return r; }
    private LocalAfterSaleRequest tasks(String num) { var r = new LocalAfterSaleRequest(); r.setRefundNum(num); r.setReturnWarehouseId(1L); r.setWarehouseId(1L); r.setReturnLogisticsCompany("退货快递"); r.setReturnLogisticsCode("RETURN-1"); r.setShippingCompanyId(1L); r.setShippingNumber("REISSUE-1"); r.setReceiverName("买家"); r.setReceiverTel("13800000000"); r.setReceiverAddress("测试地址"); return r; }
    private void sell() { var result = shipment.ship(shipRequest(), "tester"); assertEquals(0, result.getCode(), result.getMsg()); generateAndOut(result.getData(), 10); assertStock(101, 90); }
    private OrderShipRequest shipRequest() { var r = new OrderShipRequest(); r.setId("1"); r.setWarehouseId(1L); r.setShippingCompany("1"); r.setShippingNumber("SALE-1"); return r; }
    private void generateAndOut(Long stockingId, int qty) { var r = new ShipStockUpCompleteBo(); r.setStockingId(stockingId); var result = stocking.generateStockOutByOrder(r); assertEquals(0, result.getCode(), result.getMsg()); long entry = ((Number) result.getData()).longValue(); var out = new StockOutItemRequest(); out.setEntryItemId(db.queryForObject("SELECT id FROM erp_stock_out_item WHERE entry_id=?", Long.class, entry)); out.setOutQty(qty); var done = outbound.stockOut(1L, "tester", out); assertEquals(0, done.getCode(), done.getMsg()); }
    private StockInLocalRequest inRequest(int qty) { var r = new StockInLocalRequest(); r.setId(db.queryForObject("SELECT id FROM erp_stock_in", Long.class)); var i = new StockInLocalRequest.Item(); i.setItemId(db.queryForObject("SELECT id FROM erp_stock_in_item", Long.class)); i.setQuantity(qty); r.setItems(List.of(i)); return r; }
    private void receive(int qty) { var result = inbound.localStockIn(inRequest(qty), 1L, "tester"); assertEquals(0, result.getCode(), result.getMsg()); }
    private void assertStock(int sku, int qty) { assertEquals(qty, db.queryForObject("SELECT quantity FROM o_goods_inventory WHERE sku_id=?", Integer.class, sku)); assertEquals(qty, db.queryForObject("SELECT SUM(current_qty) FROM o_goods_inventory_batch WHERE sku_id=?", Integer.class, sku)); }
    private int value(String sql) { return db.queryForObject(sql, Integer.class); }
    private <T> T map(Class<T> type) { return session.getMapper(type); }
    @SuppressWarnings("unchecked") private <T> T tx(T target) { var advice = new TransactionInterceptor(); advice.setTransactionManager(transactions); advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(advice); return (T) proxy.getProxy(); }
    private <T> T inject(T target, Object mapper) throws Exception { for (Class<?> type=target.getClass(); type!=null; type=type.getSuperclass()) { try { var field=type.getDeclaredField("baseMapper"); field.setAccessible(true); field.set(target, mapper); return target; } catch (NoSuchFieldException ignored) {} } throw new IllegalStateException("baseMapper not found"); }
}
