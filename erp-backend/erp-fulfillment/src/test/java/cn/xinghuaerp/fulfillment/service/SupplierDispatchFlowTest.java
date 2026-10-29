package cn.xinghuaerp.fulfillment.service;

import cn.xinghuaerp.inventory.request.StockOutItemRequest;
import cn.xinghuaerp.order.request.LocalAfterSaleRequest;
import cn.xinghuaerp.purchase.request.SupplierDispatchConfirmRequest;
import cn.xinghuaerp.purchase.request.SupplierDispatchRequest;
import cn.xinghuaerp.inventory.mapper.OGoodsInventoryBatchMapper;
import cn.xinghuaerp.inventory.mapper.OGoodsInventoryMapper;
import cn.xinghuaerp.product.mapper.OGoodsMapper;
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
import cn.xinghuaerp.order.mapper.ShopOrderItemMapper;
import cn.xinghuaerp.order.mapper.ShopOrderMapper;
import cn.xinghuaerp.order.mapper.ShopRefundMapper;
import cn.xinghuaerp.system.mapper.ErpLogisticsCompanyMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.product.mapper.ErpSupplierProductItemMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierProductMapper;
import cn.xinghuaerp.order.facade.OrderFacadeImpl;
import cn.xinghuaerp.inventory.facade.InventoryFacadeImpl;
import cn.xinghuaerp.system.facade.SystemFacadeImpl;
import cn.xinghuaerp.product.facade.ProductFacadeImpl;
import cn.xinghuaerp.purchase.facade.PurchaseFacadeImpl;
import cn.xinghuaerp.order.bo.OrderShipRequest;
import cn.xinghuaerp.inventory.bo.ShipStockUpCompleteBo;
import cn.xinghuaerp.order.request.LocalAfterSaleRequest;
import cn.xinghuaerp.inventory.request.StockOutItemRequest;
import cn.xinghuaerp.purchase.request.SupplierDispatchConfirmRequest;
import cn.xinghuaerp.purchase.request.SupplierDispatchRequest;
import cn.xinghuaerp.product.service.OGoodsSkuService;
import cn.xinghuaerp.inventory.service.impl.OGoodsInventoryBatchServiceImpl;
import cn.xinghuaerp.inventory.service.impl.OGoodsInventoryServiceImpl;
import cn.xinghuaerp.inventory.service.impl.ErpStockOutItemServiceImpl;
import cn.xinghuaerp.inventory.service.impl.ErpStockOutServiceImpl;
import cn.xinghuaerp.order.service.LocalAfterSaleService;
import cn.xinghuaerp.system.service.SalesScope;
import cn.xinghuaerp.order.service.impl.OOrderItemServiceImpl;
import cn.xinghuaerp.inventory.service.impl.OOrderStockingItemBatchServiceImpl;
import cn.xinghuaerp.order.service.impl.OOrderStockingItemServiceImpl;
import cn.xinghuaerp.fulfillment.service.ManualShipmentService;
import cn.xinghuaerp.purchase.service.SupplierDispatchService;
import cn.xinghuaerp.utils.UUID;
import java.math.BigDecimal;
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
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SupplierDispatchFlowTest {
    JdbcTemplate db; SupplierDispatchService service; ManualShipmentService manual; OOrderStockingItemServiceImpl stocking; ErpStockOutServiceImpl outbound; LocalAfterSaleService after;
    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        new ResourceDatabasePopulator(new ClassPathResource("sales-shipping-schema.sql"), new ClassPathResource("local-after-sale-schema.sql"), new ClassPathResource("supplier-dispatch-schema.sql")).execute(ds);
        db=new JdbcTemplate(ds);
        var config=new MybatisConfiguration();
        for (var mapper: List.of(OOrderMapper.class,OOrderItemMapper.class,OOrderStockingMapper.class,OOrderStockingItemMapper.class,ErpSupplierMapper.class,ErpSupplierProductItemMapper.class,ErpSupplierProductMapper.class,OGoodsSkuMapper.class,ErpLogisticsCompanyMapper.class,ShopOrderMapper.class,ShopOrderItemMapper.class,ErpWarehouseMapper.class,ErpStockOutMapper.class,ErpStockOutItemMapper.class,OGoodsInventoryMapper.class,OGoodsInventoryBatchMapper.class,OOrderStockingItemBatchMapper.class,ShopRefundMapper.class,ORefundMapper.class,ORefundAfterSaleMapper.class,ErpStockInMapper.class,ErpStockInItemMapper.class)) config.addMapper(mapper);
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config); var session=new SqlSessionTemplate(factory.getObject());
        var orderFacade=new OrderFacadeImpl(null,null,session.getMapper(OOrderItemMapper.class),session.getMapper(OOrderMapper.class),session.getMapper(ShopOrderItemMapper.class),session.getMapper(ShopOrderMapper.class));
        var inventoryFacade=new InventoryFacadeImpl(session.getMapper(ErpStockInItemMapper.class),session.getMapper(ErpStockInMapper.class),session.getMapper(ErpStockOutItemMapper.class),session.getMapper(ErpStockOutMapper.class),session.getMapper(ErpWarehouseMapper.class),session.getMapper(OOrderStockingItemMapper.class),session.getMapper(OOrderStockingMapper.class));
        var purchaseFacade=new PurchaseFacadeImpl(null,session.getMapper(ErpSupplierMapper.class),session.getMapper(ErpSupplierProductMapper.class));
        var productFacade=new ProductFacadeImpl(session.getMapper(ErpSupplierProductItemMapper.class),null,session.getMapper(OGoodsSkuMapper.class),null,null);
        var systemFacade=new SystemFacadeImpl(session.getMapper(ErpLogisticsCompanyMapper.class),null,null);
        var target=new SupplierDispatchService(orderFacade,orderFacade,inventoryFacade,inventoryFacade,purchaseFacade,inventoryFacade,productFacade,purchaseFacade,productFacade,systemFacade,orderFacade,orderFacade);
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource())); service=(SupplierDispatchService)proxy.getProxy();
        var manualTarget=new ManualShipmentService(orderFacade,orderFacade,inventoryFacade,inventoryFacade,inventoryFacade,systemFacade,productFacade);
        var manualProxy=new ProxyFactory(manualTarget); manualProxy.setProxyTargetClass(true); manualProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource())); manual=(ManualShipmentService)manualProxy.getProxy();
        var txManager=new DataSourceTransactionManager(ds);
        stocking=tx(new OOrderStockingItemServiceImpl(inventoryFacade,inventoryFacade,inventoryFacade,inventoryFacade,inventoryFacade),txManager);
        var outItems=inject(new ErpStockOutItemServiceImpl(session.getMapper(ErpStockOutItemMapper.class)),session.getMapper(ErpStockOutItemMapper.class));
        var inventory=inject(new OGoodsInventoryServiceImpl(),session.getMapper(OGoodsInventoryMapper.class));
        var batches=inject(new OGoodsInventoryBatchServiceImpl(),session.getMapper(OGoodsInventoryBatchMapper.class));
        var records=inject(new OOrderStockingItemBatchServiceImpl(),session.getMapper(OOrderStockingItemBatchMapper.class));
        outbound=tx(new ErpStockOutServiceImpl(session.getMapper(ErpStockOutMapper.class),outItems,null,records,stocking,session.getMapper(OOrderStockingMapper.class),inventory,batches),txManager);
        after=tx(new LocalAfterSaleService(session.getMapper(OOrderMapper.class),session.getMapper(OOrderItemMapper.class),productFacade,session.getMapper(ShopRefundMapper.class),session.getMapper(ORefundMapper.class),session.getMapper(ORefundAfterSaleMapper.class),inventoryFacade,systemFacade,inventoryFacade,inventoryFacade,inventoryFacade,inventoryFacade,inventoryFacade,inventoryFacade,session.getMapper(ShopOrderMapper.class),purchaseFacade,purchaseFacade,productFacade),txManager);
        db.update("INSERT INTO erp_supplier(id,name,number,is_shipper,disable,is_delete,merchant_id) VALUES(1,'供应商','S1',1,0,0,0)");
        db.update("INSERT INTO erp_supplier_product(id,supplier_id,erp_goods_id,status) VALUES(1,1,201,1)");
        db.update("INSERT INTO erp_supplier_product_item(id,supplier_product_id,supplier_id,erp_goods_id,erp_goods_sku_id,status) VALUES(1,1,1,201,101,1)");
        db.update("INSERT INTO erp_warehouse(id,warehouse_type,warehouse_no,warehouse_name,status,merchant_id,warehouse_source) VALUES(1,'LOCAL','MAIN','主仓',1,0,0)");
        db.update("INSERT INTO erp_logistics_company(id,name,code,status) VALUES(1,'快递','EXP',1)");
        db.update("INSERT INTO o_goods_sku(id,goods_id,sku_code,ship_type,merchant_id) VALUES(101,201,'SKU',0,0)");
        db.update("INSERT INTO o_order(id,order_mode,order_num,shop_type,shop_id,merchant_id,order_status,amount) VALUES(1,1,'SALE-1',999,10,0,1,100)");
        db.update("INSERT INTO o_order_item(id,order_id,order_num,sub_order_num,goods_id,goods_sku_id,goods_title,goods_price,payment,item_amount,quantity,refund_status,refund_count,sku_id,shop_type,shop_id) VALUES(1,1,'SALE-1','SUB-1',201,101,'电饭煲',10,100,100,10,1,0,'SKU',999,10)");
        db.update("INSERT INTO o_goods_inventory(id,goods_id,sku_id,quantity,available_quantity,warehouse_id) VALUES(1,201,101,100,100,1)");
        db.update("INSERT INTO o_goods_inventory_batch(id,inventory_id,batch_num,goods_id,sku_id,origin_qty,current_qty,warehouse_id) VALUES(1,1,'BATCH',201,101,100,100,1)");
        db.update("INSERT INTO oms_shop_order(id,merchant_id,shop_id,order_id,order_status,goods_amount,order_amount,payment_amount,order_mode) VALUES(1,0,10,'SALE-1',1,100,100,100,1)");
        db.update("INSERT INTO oms_shop_order_item(id,shop_order_id,sub_order_id) VALUES(1,1,'SUB-1')");
    }
    SupplierDispatchRequest request(Long... ids) { var r=new SupplierDispatchRequest(); r.setIds(List.of(ids)); r.setSupplierId(1L); return r; }
    int value(String sql) {return db.queryForObject(sql,Integer.class);}
    @Test void allocationAndConfirmationKeepLocalInventory() {
        long id=service.allocate(request(1L),"tester").get(0);
        assertEquals(0,value("SELECT send_status FROM o_order_stocking")); assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
        var r=new SupplierDispatchConfirmRequest(); r.setId(id); r.setLogisticsCompany(1L); r.setWaybillCode("TEST-1"); service.confirm(r,"tester");
        assertEquals(2,value("SELECT send_status FROM o_order_stocking")); assertEquals(2,value("SELECT order_status FROM o_order")); assertEquals(2,value("SELECT order_status FROM oms_shop_order")); assertEquals(2,value("SELECT ship_status FROM oms_shop_order_item")); assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
        assertThrows(IllegalArgumentException.class,()->service.confirm(r,"tester"));
    }
    @Test void failedBatchRollsBackEarlierOrder() {
        db.update("INSERT INTO o_order(id,order_mode,order_num,shop_type,shop_id,merchant_id,order_status,amount) VALUES(2,1,'SALE-2',999,10,0,1,100)");
        assertThrows(IllegalArgumentException.class,()->service.allocate(request(1L,2L),"tester"));
        assertEquals(0,value("SELECT COUNT(*) FROM o_order_stocking")); assertEquals(0,value("SELECT ship_status FROM o_order_item WHERE id=1"));
    }
    @Test void rejectsUnavailableOrMismatchedOffer() {
        db.update("UPDATE erp_supplier_product SET status=2 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->service.allocate(request(1L),"tester"));
        db.update("UPDATE erp_supplier_product SET status=1,supplier_id=2 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->service.allocate(request(1L),"tester"));
        db.update("UPDATE erp_supplier_product SET supplier_id=1 WHERE id=1");
        db.update("UPDATE erp_supplier_product_item SET status=2 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->service.allocate(request(1L),"tester"));
        assertEquals(0,value("SELECT COUNT(*) FROM o_order_stocking"));
    }
    @Test void failedConfirmationRollsBackAllUpdates() {
        long id=service.allocate(request(1L),"tester").get(0);
        db.execute("ALTER TABLE oms_shop_order ADD CONSTRAINT fail_ship CHECK(order_status <> 2)");
        var r=new SupplierDispatchConfirmRequest(); r.setId(id); r.setLogisticsCompany(1L); r.setWaybillCode("TEST-1");
        assertThrows(RuntimeException.class,()->service.confirm(r,"tester"));
        assertEquals(0,value("SELECT send_status FROM o_order_stocking"));
        assertEquals(1,value("SELECT ship_status FROM o_order_item"));
        assertEquals(1,value("SELECT order_status FROM o_order"));
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
    }
    @Test void validatesMerchantSkuRefundAndOrderMode() {
        db.update("UPDATE o_order SET merchant_id=7 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->service.allocate(request(1L),"tester"));
        db.update("UPDATE erp_supplier SET merchant_ids='7,8' WHERE id=1");
        db.update("UPDATE o_order_item SET goods_id=202 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->service.allocate(request(1L),"tester"));
        db.update("UPDATE o_order_item SET goods_id=201,refund_status=2 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->service.allocate(request(1L),"tester"));
        db.update("UPDATE o_order_item SET refund_status=1 WHERE id=1");
        db.update("UPDATE o_order SET order_mode=0 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->service.allocate(request(1L),"tester"));
        assertEquals(0,value("SELECT COUNT(*) FROM o_order_stocking"));
    }
    @Test void skuChangeWaitsForAllocationAndRejectsChangedState() throws Exception {
        var ds = db.getDataSource();
        var config = new MybatisConfiguration();
        for (var mapper : List.of(OOrderMapper.class, OOrderItemMapper.class, ErpSupplierMapper.class,
                OGoodsSkuMapper.class, OGoodsMapper.class, ErpWarehouseMapper.class)) config.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        var skuService = org.mockito.Mockito.mock(cn.xinghuaerp.product.facade.OGoodsSkuFacade.class);
        var purchaseFacade2 = new PurchaseFacadeImpl(null, session.getMapper(ErpSupplierMapper.class), null);
        var productFacade2 = new ProductFacadeImpl(null, session.getMapper(OGoodsMapper.class), session.getMapper(OGoodsSkuMapper.class), null, null);
        var inventoryFacade2 = new InventoryFacadeImpl(null, null, null, null, session.getMapper(ErpWarehouseMapper.class), null, null);
        var target = new OOrderItemServiceImpl(session.getMapper(OOrderItemMapper.class),session.getMapper(OOrderMapper.class),
                purchaseFacade2, productFacade2, skuService,
                productFacade2, inventoryFacade2);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        var skuUpdate = (OOrderItemServiceImpl) proxy.getProxy();
        var pool = Executors.newSingleThreadExecutor();
        try (var conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (var statement = conn.prepareStatement("SELECT id FROM o_order WHERE id=1 FOR UPDATE")) { statement.executeQuery().close(); }
            var started = new CountDownLatch(1);
            var future = pool.submit(() -> { started.countDown(); return skuUpdate.updateErpSkuId(1L,"101"); });
            assertTrue(started.await(2,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> future.get(200,TimeUnit.MILLISECONDS));
            try (var statement = conn.prepareStatement("UPDATE o_order_item SET ship_status=1 WHERE id=1")) { statement.executeUpdate(); }
            conn.commit();
            assertNotEquals(0,future.get(5,TimeUnit.SECONDS).getCode());
            assertEquals(101,value("SELECT goods_sku_id FROM o_order_item WHERE id=1"));
            org.mockito.Mockito.verifyNoInteractions(skuService);
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentAllocationCreatesOnlyOneTask() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Boolean> call=()-> { start.await(); try { service.allocate(request(1L),"tester"); return true; } catch (IllegalArgumentException rejected) { return false; } };
            var a=pool.submit(call); var b=pool.submit(call); start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(1,value("SELECT COUNT(*) FROM o_order_stocking"));
        } finally { pool.shutdownNow(); }
    }
    @Test void cancelRetainsHistoryAndAllowsReallocation() {
        long id=service.allocate(request(1L),"tester").get(0);
        service.cancel(id,"tester");
        assertEquals(-1,value("SELECT send_status FROM o_order_stocking WHERE id="+id));
        assertEquals(0,value("SELECT dist_status FROM o_order WHERE id=1"));
        assertEquals(0,value("SELECT has_push_erp FROM o_order_item WHERE id=1"));
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
        assertThrows(IllegalArgumentException.class,()->service.cancel(id,"tester"));
        service.allocate(request(1L),"tester");
        assertEquals(2,value("SELECT COUNT(*) FROM o_order_stocking"));
    }
    @Test void cancelledTaskAllowsLocalShipment() {
        long id=service.allocate(request(1L),"tester").get(0); service.cancel(id,"tester");
        var r=new OrderShipRequest(); r.setId("1"); r.setWarehouseId(1L); r.setShippingCompany("1"); r.setShippingNumber("LOCAL-1");
        assertEquals(0,manual.ship(r,"tester").getCode());
        assertEquals(2,value("SELECT order_status FROM o_order WHERE id=1"));
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
    }
    @Test void cannotCancelShippedOrRefundingTask() {
        long id=service.allocate(request(1L),"tester").get(0);
        db.update("UPDATE o_order_item SET refund_status=2 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->service.cancel(id,"tester"));
        db.update("UPDATE o_order_item SET refund_status=1 WHERE id=1");
        var r=new SupplierDispatchConfirmRequest(); r.setId(id); r.setLogisticsCompany(1L); r.setWaybillCode("TEST-1"); service.confirm(r,"tester");
        assertThrows(IllegalArgumentException.class,()->service.cancel(id,"tester"));
    }
    private void secondSupplierAndItem() {
        db.update("INSERT INTO erp_supplier(id,name,number,is_shipper,disable,is_delete,merchant_id) VALUES(2,'供应商二','S2',1,0,0,0)");
        db.update("INSERT INTO erp_supplier_product(id,supplier_id,erp_goods_id,status) VALUES(2,2,202,1)");
        db.update("INSERT INTO erp_supplier_product_item(id,supplier_product_id,supplier_id,erp_goods_id,erp_goods_sku_id,status) VALUES(2,2,2,202,102,1)");
        db.update("INSERT INTO o_goods_sku(id,goods_id,sku_code,ship_type,merchant_id) VALUES(102,202,'SKU2',0,0)");
        db.update("INSERT INTO o_order_item(id,order_id,order_num,sub_order_num,goods_id,goods_sku_id,goods_title,goods_price,quantity,refund_status,refund_count,sku_id,shop_type,shop_id) VALUES(2,1,'SALE-1','SUB-2',202,102,'商品二',10,1,1,0,'SKU2',999,10)");
        db.update("INSERT INTO oms_shop_order_item(id,shop_order_id,sub_order_id) VALUES(2,1,'SUB-2')");
    }
    private SupplierDispatchRequest itemRequest(long supplier, Long... ids) { var r=request(ids); r.setSupplierId(supplier); return r; }
    private void confirmTask(long id, String code) { var r=new SupplierDispatchConfirmRequest(); r.setId(id); r.setLogisticsCompany(1L); r.setWaybillCode(code); service.confirm(r,"tester"); }
    @Test void twoSuppliersSplitAndShipWithoutLocalStockChange() {
        secondSupplierAndItem(); long first=service.allocateItems(itemRequest(1,1L),"tester").get(0);
        assertEquals(1,value("SELECT dist_status FROM o_order WHERE id=1"));
        assertThrows(IllegalArgumentException.class,()->confirmTask(first,"A"));
        long second=service.allocateItems(itemRequest(2,2L),"tester").get(0);
        assertEquals(2,value("SELECT dist_status FROM o_order WHERE id=1"));
        confirmTask(first,"A"); assertEquals(101,value("SELECT order_status FROM o_order")); assertEquals(101,value("SELECT order_status FROM oms_shop_order"));
        assertEquals(2,value("SELECT ship_status FROM o_order_item WHERE id=1")); assertEquals(1,value("SELECT ship_status FROM o_order_item WHERE id=2"));
        confirmTask(second,"B"); assertEquals(2,value("SELECT order_status FROM o_order")); assertEquals(2,value("SELECT order_status FROM oms_shop_order"));
        assertEquals(2,value("SELECT count(*) FROM oms_shop_order_item WHERE ship_status=2")); assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
        assertNull(db.queryForObject("SELECT waybill_code FROM o_order",String.class));
        assertThrows(IllegalArgumentException.class,()->confirmTask(second,"B"));
    }
    @Test void splitCanCancelOnePendingTaskAndReassign() {
        secondSupplierAndItem(); long first=service.allocateItems(itemRequest(1,1L),"tester").get(0);
        long second=service.allocateItems(itemRequest(2,2L),"tester").get(0); service.cancel(first,"tester");
        assertEquals(1,value("SELECT dist_status FROM o_order WHERE id=1"));
        assertEquals(1,value("SELECT has_push_erp FROM o_order_item WHERE id=2"));
        long replacement=service.allocateItems(itemRequest(1,1L),"tester").get(0);
        confirmTask(second,"B"); confirmTask(replacement,"A"); assertEquals(2,value("SELECT order_status FROM o_order"));
    }
    @Test void splitCanReassignRemainingItemAfterFirstShipment() {
        secondSupplierAndItem(); long first=service.allocateItems(itemRequest(1,1L),"tester").get(0);
        long second=service.allocateItems(itemRequest(2,2L),"tester").get(0); confirmTask(first,"A");
        service.cancel(second,"tester"); assertEquals(101,value("SELECT order_status FROM o_order"));
        long replacement=service.allocateItems(itemRequest(2,2L),"tester").get(0); confirmTask(replacement,"B");
        assertEquals(2,value("SELECT order_status FROM o_order"));
    }
    @Test void concurrentItemAllocationIsSerialized() throws Exception {
        secondSupplierAndItem(); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Boolean> call=()->{start.await();try { service.allocateItems(itemRequest(1,1L),"tester");return true; } catch(IllegalArgumentException e) { return false; }};
            var a=pool.submit(call); var b=pool.submit(call); start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)); assertEquals(1,value("SELECT COUNT(*) FROM o_order_stocking"));
        } finally { pool.shutdownNow(); }
    }
    @Test void splitRejectsDuplicateAndWrongSupplierAtomically() {
        secondSupplierAndItem();
        assertThrows(IllegalArgumentException.class,()->service.allocateItems(itemRequest(1,1L,2L),"tester"));
        assertEquals(0,value("SELECT COUNT(*) FROM o_order_stocking"));
        service.allocateItems(itemRequest(1,1L),"tester");
        assertThrows(IllegalArgumentException.class,()->service.allocateItems(itemRequest(1,1L),"tester"));
        assertEquals(1,value("SELECT COUNT(*) FROM o_order_stocking"));
    }
    private void secondSupplierSameSku() {
        db.update("INSERT INTO erp_supplier(id,name,number,is_shipper,disable,is_delete,merchant_id) VALUES(2,'供应商二','S2',1,0,0,0)");
        db.update("INSERT INTO erp_supplier_product(id,supplier_id,erp_goods_id,status) VALUES(2,2,201,1)");
        db.update("INSERT INTO erp_supplier_product_item(id,supplier_product_id,supplier_id,erp_goods_id,erp_goods_sku_id,status) VALUES(2,2,2,201,101,1)");
    }
    private SupplierDispatchRequest quantityRequest(long supplier, int quantity) {
        var r=itemRequest(supplier,1L); r.setQuantities(Map.of("1",quantity)); return r;
    }
    @Test void sameItemCanBeSplitByQuantityAndShippedSeparately() {
        secondSupplierSameSku(); long a=service.allocateItems(quantityRequest(1,4),"tester").get(0);
        assertEquals(0,value("SELECT has_push_erp FROM o_order_item WHERE id=1"));
        assertEquals(6,service.remainingItems(List.of(1L)).get("1"));
        assertThrows(IllegalArgumentException.class,()->service.allocateItems(quantityRequest(2,0),"tester"));
        assertThrows(IllegalArgumentException.class,()->confirmTask(a,"A"));
        long b=service.allocateItems(quantityRequest(2,6),"tester").get(0);
        assertEquals(2,value("SELECT dist_status FROM o_order")); confirmTask(a,"A");
        assertEquals(101,value("SELECT order_status FROM o_order")); assertEquals(1,value("SELECT ship_status FROM oms_shop_order_item"));
        assertNull(db.queryForObject("SELECT waybill_code FROM o_order_item",String.class));
        confirmTask(b,"B"); assertEquals(2,value("SELECT order_status FROM o_order")); assertEquals(2,value("SELECT ship_status FROM oms_shop_order_item"));
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
    }
    @Test void cancelledPartialQuantityCanBeReassignedAfterOtherShipment() {
        secondSupplierSameSku(); long a=service.allocateItems(quantityRequest(1,4),"tester").get(0);
        long b=service.allocateItems(quantityRequest(2,6),"tester").get(0); confirmTask(a,"A"); service.cancel(b,"tester");
        assertThrows(IllegalArgumentException.class,()->service.allocateItems(quantityRequest(2,7),"tester"));
        long c=service.allocateItems(quantityRequest(2,6),"tester").get(0); confirmTask(c,"C"); assertEquals(2,value("SELECT order_status FROM o_order"));
    }
    @Test void concurrentPartialAssignmentsCannotExceedQuantity() throws Exception {
        secondSupplierSameSku(); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Boolean> call=()->{start.await();try {service.allocateItems(quantityRequest(1,6),"tester");return true;}catch(IllegalArgumentException e){return false;}};
            var a=pool.submit(call); var b=pool.submit(call); start.countDown(); assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(6,value("SELECT SUM(quantity) FROM o_order_stocking_item"));
        } finally {pool.shutdownNow();}
    }
    private SupplierDispatchRequest localRequest(int qty) { var r=quantityRequest(1,qty); r.setWarehouseId(1L); return r; }
    private void confirmLocal(long id) { var r=new SupplierDispatchConfirmRequest(); r.setId(id); r.setLogisticsCompany(1L); r.setWaybillCode("LOCAL"); service.confirmLocal(r,"tester"); }
    @Test void mixedSupplierAndLocalShipAndOnlyLocalActualStockOutDeducts() {
        long local=service.allocateLocalItems(localRequest(4),"tester").get(0);
        long vendor=service.allocateItems(quantityRequest(1,6),"tester").get(0);
        var generate=new ShipStockUpCompleteBo(); generate.setStockingId(local);
        assertNotEquals(0,stocking.generateStockOutByOrder(generate).getCode());
        confirmTask(vendor,"VENDOR"); assertEquals(101,value("SELECT order_status FROM o_order"));
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
        confirmLocal(local); assertEquals(2,value("SELECT order_status FROM o_order"));
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
        assertEquals(0,stocking.generateStockOutByOrder(generate).getCode());
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
        var request=new StockOutItemRequest(); request.setEntryItemId(db.queryForObject("SELECT id FROM erp_stock_out_item",Long.class)); request.setOutQty(4); request.setOutQuantity(0);
        assertEquals(0,outbound.stockOut(1L,"tester",request).getCode());
        assertEquals(96,value("SELECT quantity FROM o_goods_inventory")); assertEquals(96,value("SELECT current_qty FROM o_goods_inventory_batch"));
        assertNotEquals(0,outbound.stockOut(1L,"tester",request).getCode());
    }
    @Test void mixedFulfillmentThroughSupplierReturnAndReissue() {
        long local=service.allocateLocalItems(localRequest(4),"tester").get(0);
        long vendor=service.allocateItems(quantityRequest(1,6),"tester").get(0);
        confirmTask(vendor,"VENDOR"); confirmLocal(local);
        var generate=new ShipStockUpCompleteBo(); generate.setStockingId(local); assertEquals(0,stocking.generateStockOutByOrder(generate).getCode());
        var out=new StockOutItemRequest(); out.setEntryItemId(db.queryForObject("SELECT id FROM erp_stock_out_item",Long.class)); out.setOutQty(4); out.setOutQuantity(0);
        assertEquals(0,outbound.stockOut(1L,"tester",out).getCode()); assertEquals(96,value("SELECT quantity FROM o_goods_inventory"));
        var scope=new SalesScope(null,null); var create=new LocalAfterSaleRequest();
        create.setShopId(10L); create.setOrderItemId(1L); create.setType(10); create.setQuantity(4); create.setRefundAmount(new BigDecimal("40")); create.setReason("退货");
        after.create(create,scope,"tester"); String refund=db.queryForObject("SELECT refund_num FROM o_refund",String.class); after.process(refund,scope,"tester");
        var r=new LocalAfterSaleRequest(); r.setRefundNum(refund); r.setReturnType(300); r.setReturnSupplierId(1L); r.setReturnLogisticsCompany("退货快递"); r.setReturnLogisticsCode("RETURN"); after.confirm(r,scope,"tester");
        assertThrows(IllegalArgumentException.class,()->after.complete(r,scope,"tester")); r.setReturnReceived(true); r.setRefundPaid(true); r.setRemark("确认供应商收货及退款"); after.complete(r,scope,"tester");
        assertEquals(0,value("SELECT COUNT(*) FROM erp_stock_in")); assertEquals(96,value("SELECT quantity FROM o_goods_inventory"));
        create.setType(80); create.setQuantity(2); create.setRefundAmount(BigDecimal.ZERO); after.create(create,scope,"tester");
        String replacement=db.queryForObject("SELECT refund_num FROM o_refund WHERE refund_type=80",String.class); after.process(replacement,scope,"tester");
        var reissue=new LocalAfterSaleRequest(); reissue.setRefundNum(replacement); reissue.setReissueType(300); reissue.setReissueSupplierId(1L); reissue.setReceiverName("买家"); reissue.setReceiverTel("13800000000"); reissue.setReceiverAddress("测试地址");
        after.confirm(reissue,scope,"tester"); assertThrows(IllegalArgumentException.class,()->after.complete(reissue,scope,"tester"));
        reissue.setShippingCompanyId(1L); reissue.setShippingNumber("REISSUE"); after.confirmVendorReissue(reissue,scope,"tester"); after.complete(reissue,scope,"tester");
        assertEquals(96,value("SELECT quantity FROM o_goods_inventory")); assertEquals(2,value("SELECT COUNT(*) FROM o_refund WHERE status=4"));
    }
    @Test void twoMerchantsRequireExplicitSupplierAuthorization() {
        db.update("UPDATE erp_supplier SET merchant_ids='7' WHERE id=1");
        db.update("UPDATE o_order SET merchant_id=7 WHERE id=1");
        db.update("UPDATE oms_shop_order SET merchant_id=7 WHERE id=1");
        db.update("INSERT INTO erp_supplier(id,name,number,is_shipper,disable,is_delete,merchant_id) VALUES(2,'商户八供应商','S2',1,0,0,8)");
        db.update("INSERT INTO erp_supplier_product(id,supplier_id,erp_goods_id,status) VALUES(2,2,201,1)");
        db.update("INSERT INTO erp_supplier_product_item(id,supplier_product_id,supplier_id,erp_goods_id,erp_goods_sku_id,status) VALUES(2,2,2,201,101,1)");
        db.update("INSERT INTO o_order(id,order_mode,order_num,shop_type,shop_id,merchant_id,order_status,amount) VALUES(2,1,'SALE-2',999,20,8,1,100)");
        db.update("INSERT INTO o_order_item(id,order_id,order_num,sub_order_num,goods_id,goods_sku_id,goods_title,goods_price,quantity,refund_status,refund_count,sku_id,shop_type,shop_id) VALUES(2,2,'SALE-2','SUB-2',201,101,'电饭煲',10,2,1,0,'SKU',999,20)");
        db.update("INSERT INTO oms_shop_order(id,merchant_id,shop_id,order_id,order_status,goods_amount,order_amount,payment_amount,order_mode) VALUES(2,8,20,'SALE-2',1,100,100,100,1)");
        db.update("INSERT INTO oms_shop_order_item(id,shop_order_id,sub_order_id) VALUES(2,2,'SUB-2')");
        assertThrows(IllegalArgumentException.class,()->service.allocate(itemRequest(1,2L),"tester"));
        assertThrows(IllegalArgumentException.class,()->service.allocate(itemRequest(2,1L),"tester"));
        assertEquals(0,value("SELECT COUNT(*) FROM o_order_stocking"));
        long a=service.allocate(itemRequest(1,1L),"tester").get(0); long b=service.allocate(itemRequest(2,2L),"tester").get(0);
        confirmTask(a,"A"); confirmTask(b,"B");
        assertEquals(1,value("SELECT COUNT(*) FROM o_order_stocking WHERE merchant_id=7 AND shipper_id=1 AND send_status=2"));
        assertEquals(1,value("SELECT COUNT(*) FROM o_order_stocking WHERE merchant_id=8 AND shipper_id=2 AND send_status=2"));
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
    }
    @Test void mixedLocalCancellationReturnsOnlyItsQuantity() {
        long local=service.allocateLocalItems(localRequest(4),"tester").get(0); service.allocateItems(quantityRequest(1,6),"tester");
        service.cancelLocal(local,"tester"); assertEquals(4,service.remainingItems(List.of(1L)).get("1"));
        assertThrows(IllegalArgumentException.class,()->service.allocateLocalItems(localRequest(5),"tester"));
        long replacement=service.allocateLocalItems(localRequest(4),"tester").get(0); confirmLocal(replacement);
        assertThrows(IllegalArgumentException.class,()->service.cancelLocal(replacement,"tester"));
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
    }
    @Test void mixedConcurrentAssignmentsCannotExceedQuantity() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await();try {service.allocateLocalItems(localRequest(6),"tester");return true;}catch(IllegalArgumentException e){return false;}});
            var b=pool.submit(()->{start.await();try {service.allocateItems(quantityRequest(1,6),"tester");return true;}catch(IllegalArgumentException e){return false;}});
            start.countDown(); assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(6,value("SELECT SUM(quantity) FROM o_order_stocking_item")); assertEquals(100,value("SELECT quantity FROM o_goods_inventory"));
        } finally {pool.shutdownNow();}
    }
    @Test void localConfirmationRechecksWarehouseStatus() {
        long id=service.allocateLocalItems(localRequest(10),"tester").get(0);
        db.update("UPDATE erp_warehouse SET status=0 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->confirmLocal(id));
        assertEquals(0,value("SELECT send_status FROM o_order_stocking WHERE id="+id));
    }
    @Test void mixedWarehouseMustBeLocalEnabledAndAuthorized() {
        db.update("UPDATE erp_warehouse SET merchant_id=5 WHERE id=1"); assertThrows(IllegalArgumentException.class,()->service.allocateLocalItems(localRequest(4),"tester"));
        db.update("UPDATE erp_warehouse SET merchant_id=0,warehouse_type='JDYC' WHERE id=1"); assertThrows(IllegalArgumentException.class,()->service.allocateLocalItems(localRequest(4),"tester"));
        assertEquals(0,value("SELECT COUNT(*) FROM o_order_stocking"));
    }
    @SuppressWarnings("unchecked") private <T> T tx(T target, DataSourceTransactionManager manager) {
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource())); return (T)proxy.getProxy();
    }
    private <T> T inject(T service,Object mapper) throws Exception {
        for(Class<?> c=service.getClass();c!=null;c=c.getSuperclass()) try { var field=c.getDeclaredField("baseMapper");field.setAccessible(true);field.set(service,mapper);return service; } catch(NoSuchFieldException ignored) {}
        throw new IllegalStateException("baseMapper not found");
    }
    @Test void refusesInvalidSupplierAndDuplicate() {
        db.update("UPDATE erp_supplier SET is_shipper=0 WHERE id=1"); assertThrows(IllegalArgumentException.class,()->service.allocate(request(1L),"tester"));
        db.update("UPDATE erp_supplier SET is_shipper=1 WHERE id=1"); service.allocate(request(1L),"tester"); assertThrows(IllegalArgumentException.class,()->service.allocate(request(1L),"tester"));
    }
}
