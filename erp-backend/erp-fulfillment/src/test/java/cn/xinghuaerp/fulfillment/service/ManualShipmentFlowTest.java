package cn.xinghuaerp.fulfillment.service;

import cn.xinghuaerp.inventory.mapper.OGoodsInventoryBatchMapper;
import cn.xinghuaerp.inventory.mapper.OGoodsInventoryMapper;
import cn.xinghuaerp.product.mapper.OGoodsSkuMapper;
import cn.xinghuaerp.inventory.mapper.ErpStockOutItemMapper;
import cn.xinghuaerp.inventory.mapper.ErpStockOutMapper;
import cn.xinghuaerp.inventory.mapper.ErpWarehouseMapper;
import cn.xinghuaerp.order.mapper.OOrderItemMapper;
import cn.xinghuaerp.order.mapper.OOrderMapper;
import cn.xinghuaerp.inventory.mapper.OOrderStockingItemBatchMapper;
import cn.xinghuaerp.inventory.mapper.OOrderStockingItemMapper;
import cn.xinghuaerp.inventory.mapper.OOrderStockingMapper;
import cn.xinghuaerp.system.mapper.ErpLogisticsCompanyMapper;
import cn.xinghuaerp.order.bo.OrderShipRequest;
import cn.xinghuaerp.inventory.bo.ShipStockUpCompleteBo;
import cn.xinghuaerp.inventory.request.StockOutItemRequest;
import cn.xinghuaerp.inventory.service.impl.OGoodsInventoryBatchServiceImpl;
import cn.xinghuaerp.inventory.service.impl.OGoodsInventoryServiceImpl;
import cn.xinghuaerp.inventory.service.impl.ErpStockOutItemServiceImpl;
import cn.xinghuaerp.inventory.service.impl.ErpStockOutServiceImpl;
import cn.xinghuaerp.inventory.service.impl.OOrderStockingItemBatchServiceImpl;
import cn.xinghuaerp.order.service.impl.OOrderStockingItemServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real mapper + transaction tests against an isolated database, never the user's MySQL. */
class ManualShipmentFlowTest {
    private JdbcTemplate db;
    private SqlSessionTemplate session;
    private DataSourceTransactionManager transactions;
    private ManualShipmentService shipment;
    private OOrderStockingItemServiceImpl stocking;
    private ErpStockOutServiceImpl outbound;

    @BeforeEach
    void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        new ResourceDatabasePopulator(new ClassPathResource("sales-shipping-schema.sql")).execute(ds);
        db = new JdbcTemplate(ds);
        transactions = new DataSourceTransactionManager(ds);
        MybatisConfiguration config = new MybatisConfiguration();
        for (Class<?> mapper : List.of(OOrderMapper.class, OOrderItemMapper.class, OOrderStockingMapper.class,
                OOrderStockingItemMapper.class, ErpWarehouseMapper.class, ErpLogisticsCompanyMapper.class,
                OGoodsSkuMapper.class, ErpStockOutMapper.class, ErpStockOutItemMapper.class,
                OGoodsInventoryMapper.class, OGoodsInventoryBatchMapper.class, OOrderStockingItemBatchMapper.class)) {
            config.addMapper(mapper);
        }
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        session = new SqlSessionTemplate(factory.getObject());
        var orderFacade = new cn.xinghuaerp.order.facade.OrderFacadeImpl(null, null, map(OOrderItemMapper.class), map(OOrderMapper.class), null, null);
        var inventoryFacade = new cn.xinghuaerp.inventory.facade.InventoryFacadeImpl(null, null, map(ErpStockOutItemMapper.class), map(ErpStockOutMapper.class),
                map(ErpWarehouseMapper.class), map(OOrderStockingItemMapper.class), map(OOrderStockingMapper.class));
        var systemFacade = new cn.xinghuaerp.system.facade.SystemFacadeImpl(map(ErpLogisticsCompanyMapper.class), null, null);
        var productFacade = new cn.xinghuaerp.product.facade.ProductFacadeImpl(null, null, map(OGoodsSkuMapper.class), null, null);
        shipment = tx(new ManualShipmentService(orderFacade, orderFacade, inventoryFacade, inventoryFacade, inventoryFacade,
                systemFacade, productFacade));
        stocking = tx(new OOrderStockingItemServiceImpl(inventoryFacade, inventoryFacade, inventoryFacade, inventoryFacade,
                inventoryFacade));
        var outItems = inject(new ErpStockOutItemServiceImpl(map(ErpStockOutItemMapper.class)), map(ErpStockOutItemMapper.class));
        var batches = inject(new OGoodsInventoryBatchServiceImpl(), map(OGoodsInventoryBatchMapper.class));
        var inventory = inject(new OGoodsInventoryServiceImpl(), map(OGoodsInventoryMapper.class));
        var records = inject(new OOrderStockingItemBatchServiceImpl(), map(OOrderStockingItemBatchMapper.class));
        outbound = tx(new ErpStockOutServiceImpl(map(ErpStockOutMapper.class), outItems, null, records, stocking,
                map(OOrderStockingMapper.class), inventory, batches));
        db.update("INSERT INTO erp_warehouse(id,warehouse_type,warehouse_no,warehouse_name,status,merchant_id,warehouse_source) VALUES(1,'LOCAL','MAIN','主仓',1,0,0)");
        db.update("INSERT INTO erp_logistics_company(id,name,code,status) VALUES(1,'测试快递','TEST',1)");
        db.update("INSERT INTO o_goods_sku(id,goods_id,sku_code,ship_type,merchant_id) VALUES(101,201,'SKU-TEST-001',0,0)");
        db.update("INSERT INTO o_order(id,order_mode,order_num,shop_type,shop_id,merchant_id,order_status,amount) VALUES(1,1,'SALE-001',999,10,0,1,100)");
        db.update("INSERT INTO o_order_item(id,order_id,order_num,sub_order_num,goods_id,goods_sku_id,goods_title,goods_price,quantity,refund_status,refund_count,sku_id,shop_type,shop_id) VALUES(1,1,'SALE-001','SALE-001-1',201,101,'测试商品',10,10,1,0,'LOCAL-SKU-101',999,10)");
        db.update("INSERT INTO o_goods_inventory(id,goods_id,sku_id,quantity,available_quantity,warehouse_id) VALUES(1,201,101,100,100,1)");
        db.update("INSERT INTO o_goods_inventory_batch(id,inventory_id,batch_num,goods_id,sku_id,origin_qty,current_qty,warehouse_id) VALUES(1,1,'PURCHASE-001',201,101,100,100,1)");
    }

    @Test
    void completeFlowChanges100To90OnlyAtActualStockOut() {
        Long stockingId = ship();
        assertStock(100);
        assertEquals(2, value("SELECT order_status FROM o_order WHERE id=1"));
        assertEquals(1, value("SELECT order_mode FROM o_order WHERE id=1"));
        assertEquals(0, value("SELECT stocking_status FROM o_order_stocking WHERE id=" + stockingId));
        Long outId = generate(stockingId);
        assertStock(100);
        assertEquals(201, value("SELECT goods_id FROM erp_stock_out_item"));
        assertEquals(101, value("SELECT sku_id FROM erp_stock_out_item"));
        StockOutItemRequest request = outRequest(10);
        assertEquals(0, outbound.stockOut(1L, "tester", request).getCode());
        assertStock(90);
        assertEquals(2, value("SELECT status FROM erp_stock_out WHERE id=" + outId));
        assertEquals(10, value("SELECT SUM(quantity) FROM o_order_stocking_item_batch"));
        assertNotEquals(0, outbound.stockOut(1L, "tester", request).getCode());
        assertStock(90);
        assertNotEquals(0, shipment.ship(shipRequest(), "tester").getCode());
        assertEquals(1, value("SELECT COUNT(*) FROM o_order_stocking"));
        assertNotEquals(0, stocking.generateStockOutByOrder(stockRequest(stockingId)).getCode());
        assertEquals(1, value("SELECT COUNT(*) FROM erp_stock_out"));
    }

    @Test
    void outboundRejectsOtherMerchantsBatchAndAmbiguousInventory() {
        generate(ship());
        db.update("INSERT INTO o_goods_inventory(id,goods_id,sku_id,quantity,available_quantity,warehouse_id,merchant_id,shop_id) VALUES(20,201,101,50,50,1,9,10)");
        db.update("INSERT INTO o_goods_inventory_batch(id,inventory_id,batch_num,goods_id,sku_id,origin_qty,current_qty,warehouse_id,merchant_id,shop_id) VALUES(20,20,'FOREIGN',201,101,50,50,1,9,10)");
        var foreign = outRequest(1); foreign.setBatchId(20L);
        assertNotEquals(0, outbound.stockOut(1L,"tester",foreign).getCode());
        assertEquals(50,value("SELECT quantity FROM o_goods_inventory WHERE id=20"));
        db.update("INSERT INTO o_goods_inventory(id,goods_id,sku_id,quantity,available_quantity,warehouse_id,merchant_id,shop_id) VALUES(21,201,101,40,40,1,0,10)");
        assertNotEquals(0,outbound.stockOut(1L,"tester",outRequest(1)).getCode());
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory WHERE id=1"));
        assertEquals(40,value("SELECT quantity FROM o_goods_inventory WHERE id=21"));
    }

    @Test
    void outboundCannotUseDefectiveInventoryOrBatch() {
        generate(ship());
        db.update("UPDATE o_goods_inventory_batch SET stock_status=2 WHERE id=1");
        var request=outRequest(1); request.setBatchId(1L);
        assertNotEquals(0,outbound.stockOut(1L,"tester",request).getCode());
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory WHERE id=1"));
        db.update("UPDATE o_goods_inventory_batch SET stock_status=1 WHERE id=1");
        db.update("UPDATE o_goods_inventory SET stock_status=2 WHERE id=1");
        assertNotEquals(0,outbound.stockOut(1L,"tester",outRequest(1)).getCode());
        assertEquals(100,value("SELECT quantity FROM o_goods_inventory WHERE id=1"));
    }

    @Test
    void outboundRejectsDetailWithDifferentWarehouse() {
        generate(ship()); db.update("UPDATE erp_stock_out_item SET warehouse_id=2");
        assertNotEquals(0,outbound.stockOut(1L,"tester",outRequest(1)).getCode());
        assertStock(100);
    }

    @Test
    void validatesAllItemsBeforeWriting() {
        db.update("UPDATE o_order_item SET refund_status=2 WHERE id=1");
        assertNotEquals(0, shipment.ship(shipRequest(), "tester").getCode());
        assertEquals(0, value("SELECT COUNT(*) FROM o_order_stocking"));
        db.update("UPDATE o_order_item SET refund_status=1,goods_sku_id=999 WHERE id=1");
        assertNotEquals(0, shipment.ship(shipRequest(), "tester").getCode());
        assertEquals(0, value("SELECT COUNT(*) FROM o_order_stocking"));
        db.update("UPDATE o_order_item SET goods_sku_id=101 WHERE id=1");
        db.update("UPDATE erp_warehouse SET status=0 WHERE id=1");
        assertNotEquals(0, shipment.ship(shipRequest(), "tester").getCode());
        assertEquals(1, value("SELECT order_status FROM o_order WHERE id=1"));
    }

    @Test
    void databaseFailureRollsBackShipmentAndOrderUpdates() {
        db.execute("ALTER TABLE o_order_stocking_item ADD CONSTRAINT force_failure CHECK(quantity < 10)");
        assertThrows(RuntimeException.class, () -> shipment.ship(shipRequest(), "tester"));
        assertEquals(0, value("SELECT COUNT(*) FROM o_order_stocking"));
        assertEquals(1, value("SELECT order_status FROM o_order WHERE id=1"));
        assertEquals(0, value("SELECT ship_status FROM o_order_item WHERE id=1"));
    }

    @Test
    void databaseFailureRollsBackStockOutAndInventory() {
        generate(ship());
        db.execute("ALTER TABLE o_order_stocking_item_batch ADD CONSTRAINT force_failure CHECK(quantity < 10)");
        assertThrows(RuntimeException.class, () -> outbound.stockOut(1L, "tester", outRequest(10)));
        assertStock(100);
        assertEquals(0, value("SELECT out_quantity FROM erp_stock_out_item"));
        assertEquals(0, value("SELECT COUNT(*) FROM o_order_stocking_item_batch"));
    }

    @Test
    void partialStockOutRejectsExcessAndAllowsAnotherBatch() {
        generate(ship());
        StockOutItemRequest first = outRequest(4);
        first.setBatchId(1L);
        assertEquals(0, outbound.stockOut(1L, "tester", first).getCode());
        assertEquals(1, value("SELECT status FROM erp_stock_out_item"));
        assertNotEquals(0, outbound.stockOut(1L, "tester", outRequest(7)).getCode());
        assertStock(96);
        db.update("UPDATE o_goods_inventory_batch SET current_qty=90 WHERE id=1");
        db.update("INSERT INTO o_goods_inventory_batch(id,inventory_id,batch_num,goods_id,sku_id,origin_qty,current_qty,warehouse_id) VALUES(2,1,'PURCHASE-002',201,101,6,6,1)");
        var next = outRequest(6);
        next.setBatchId(2L);
        assertEquals(0, outbound.stockOut(1L, "tester", next).getCode());
        assertStock(90);
        assertEquals(2, value("SELECT status FROM erp_stock_out_item"));
    }

    @Test
    void insufficientStockAndWrongBatchLeaveInventoryUntouched() {
        generate(ship());
        db.update("UPDATE o_goods_inventory_batch SET current_qty=5 WHERE id=1");
        assertNotEquals(0, outbound.stockOut(1L, "tester", outRequest(10)).getCode());
        assertEquals(100, value("SELECT quantity FROM o_goods_inventory"));
        db.update("UPDATE o_goods_inventory_batch SET current_qty=100,warehouse_id=2 WHERE id=1");
        StockOutItemRequest request = outRequest(10);
        request.setBatchId(1L);
        assertNotEquals(0, outbound.stockOut(1L, "tester", request).getCode());
        assertEquals(100, value("SELECT quantity FROM o_goods_inventory"));
        assertEquals(0, value("SELECT out_quantity FROM erp_stock_out_item"));
    }

    @Test
    void concurrentShipmentCreatesOnlyOneStockingOrder() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {start.await(); return shipment.ship(shipRequest(), "tester").getCode();});
            var second = pool.submit(() -> {start.await(); return shipment.ship(shipRequest(), "tester").getCode();});
            start.countDown();
            int a = first.get(10, TimeUnit.SECONDS);
            int b = second.get(10, TimeUnit.SECONDS);
            assertTrue((a == 0) ^ (b == 0));
            assertEquals(1, value("SELECT COUNT(*) FROM o_order_stocking"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void generationUsesSavedWarehouseAndItemEntryIsAlsoAvailable() {
        Long id = ship();
        var wrong = stockRequest(id);
        wrong.setWarehouseId(2L);
        assertNotEquals(0, stocking.generateStockOutByOrder(wrong).getCode());
        assertEquals(0, value("SELECT COUNT(*) FROM erp_stock_out"));
        var request = new ShipStockUpCompleteBo();
        request.setIds(new Long[]{db.queryForObject("SELECT id FROM o_order_stocking_item", Long.class)});
        assertEquals(0, stocking.generateStockOutByItem(request).getCode());
        assertStock(100);
        assertEquals(2, value("SELECT stocking_status FROM o_order_stocking"));
    }

    @Test
    void generationFailureRollsBackHeaderAndStockingState() {
        Long id = ship();
        db.execute("ALTER TABLE erp_stock_out_item ADD CONSTRAINT force_failure CHECK(original_quantity < 10)");
        assertThrows(RuntimeException.class, () -> stocking.generateStockOutByOrder(stockRequest(id)));
        assertEquals(0, value("SELECT COUNT(*) FROM erp_stock_out"));
        assertEquals(0, value("SELECT stocking_status FROM o_order_stocking_item"));
        assertStock(100);
    }

    @Test
    void concurrentGenerationCreatesOnlyOneStockOut() throws Exception {
        Long id = ship();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {start.await();return stocking.generateStockOutByOrder(stockRequest(id)).getCode();});
            var second = pool.submit(() -> {start.await();return stocking.generateStockOutByOrder(stockRequest(id)).getCode();});
            start.countDown();
            assertTrue((first.get(10, TimeUnit.SECONDS) == 0) ^ (second.get(10, TimeUnit.SECONDS) == 0));
            assertEquals(1, value("SELECT COUNT(*) FROM erp_stock_out"));
            assertStock(100);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentActualStockOutDeductsOnlyOnce() throws Exception {
        generate(ship());
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {start.await();return outbound.stockOut(1L,"tester",outRequest(10)).getCode();});
            var second = pool.submit(() -> {start.await();return outbound.stockOut(1L,"tester",outRequest(10)).getCode();});
            start.countDown();
            assertTrue((first.get(10, TimeUnit.SECONDS) == 0) ^ (second.get(10, TimeUnit.SECONDS) == 0));
            assertStock(90);
            assertEquals(10, value("SELECT out_total FROM erp_stock_out"));
        } finally { pool.shutdownNow(); }
    }

    @Test void replayedPartialStockOutCannotDeductTwice() {
        generate(ship());
        var request=outRequest(4); request.setOutQuantity(0);
        assertEquals(0,outbound.stockOut(1L,"tester",request).getCode());
        assertNotEquals(0,outbound.stockOut(1L,"tester",request).getCode());
        assertStock(96);
        assertEquals(4,value("SELECT out_total FROM erp_stock_out"));
    }
    @Test void concurrentPartialRequestsWithSameObservedQuantityHaveOneWinner() throws Exception {
        generate(ship());
        var a=outRequest(4);a.setOutQuantity(0);var b=outRequest(4);b.setOutQuantity(0);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return outbound.stockOut(1L,"tester",a).getCode();});
            var second=pool.submit(()->{start.await();return outbound.stockOut(1L,"tester",b).getCode();});
            start.countDown();
            assertTrue((first.get(10,TimeUnit.SECONDS)==0) ^ (second.get(10,TimeUnit.SECONDS)==0));
        }
        assertStock(96);
    }

    private Long ship() {
        var result = shipment.ship(shipRequest(), "tester");
        assertEquals(0, result.getCode(), result.getMsg());
        return result.getData();
    }
    private Long generate(Long id) {
        var result = stocking.generateStockOutByOrder(stockRequest(id));
        assertEquals(0, result.getCode(), result.getMsg());
        return (Long) result.getData();
    }
    private OrderShipRequest shipRequest() {
        var request = new OrderShipRequest();
        request.setId("1");
        request.setWarehouseId(1L);
        request.setShippingCompany("1");
        request.setShippingNumber(" TEST-123 ");
        return request;
    }
    private ShipStockUpCompleteBo stockRequest(Long id) {
        var request = new ShipStockUpCompleteBo();
        request.setStockingId(id);
        return request;
    }
    private StockOutItemRequest outRequest(int quantity) {
        var request = new StockOutItemRequest();
        request.setEntryItemId(db.queryForObject("SELECT id FROM erp_stock_out_item", Long.class));
        request.setOutQty(quantity);
        request.setOutQuantity(db.queryForObject("SELECT COALESCE(out_quantity,0) FROM erp_stock_out_item WHERE id=?", Integer.class, request.getEntryItemId()));
        return request;
    }
    private int value(String sql) { return db.queryForObject(sql, Integer.class); }
    private void assertStock(int expected) {
        assertEquals(expected, value("SELECT quantity FROM o_goods_inventory"));
        assertEquals(expected, value("SELECT available_quantity FROM o_goods_inventory"));
        assertEquals(expected, value("SELECT SUM(current_qty) FROM o_goods_inventory_batch"));
    }
    private <T> T map(Class<T> type) {return session.getMapper(type);}
    @SuppressWarnings("unchecked")
    private <T> T tx(T target) {
        TransactionInterceptor advice = new TransactionInterceptor();
        advice.setTransactionManager(transactions);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(advice);
        return (T) proxy.getProxy();
    }
    private <T> T inject(T service, Object mapper) throws Exception {
        for (Class<?> type = service.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField("baseMapper");
                field.setAccessible(true);
                field.set(service, mapper);
                return service;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new IllegalStateException("baseMapper not found");
    }
}
