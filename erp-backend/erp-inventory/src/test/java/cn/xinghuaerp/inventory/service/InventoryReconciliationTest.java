package cn.xinghuaerp.inventory.service;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import static org.junit.jupiter.api.Assertions.*;
class InventoryReconciliationTest {
 @Test void differencesScopeAndCursorAreIndependent() {
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:reconcile;MODE=MySQL");
  var db=new JdbcTemplate(ds);
  try(var connection=ds.getConnection()) {
   db.execute("CREATE TABLE o_goods_inventory(id BIGINT,sku_id BIGINT,warehouse_id BIGINT,merchant_id BIGINT,shop_id BIGINT,stock_status INT,quantity INT,locked_quantity INT,available_quantity INT,is_delete INT)");
   db.execute("CREATE TABLE o_goods_inventory_batch(id BIGINT,inventory_id BIGINT,sku_id BIGINT,warehouse_id BIGINT,merchant_id BIGINT,shop_id BIGINT,stock_status INT,current_qty INT)");
   db.update("INSERT INTO o_goods_inventory VALUES(1,1,1,7,10,1,0,0,0,0),(2,2,1,7,10,1,10,2,7,0),(3,3,2,8,11,1,5,0,5,0)");
   db.update("INSERT INTO o_goods_inventory_batch VALUES(0,1,1,1,7,10,1,0),(1,2,2,1,7,10,1,8),(2,3,3,2,8,11,1,5)");
   var service=new InventoryReconciliationService(db);var scope=new Scope("SHOP",10L,7L,10L);
   var first=service.inspect(scope,0,1);assertTrue(first.hasMore());assertTrue(first.differences().isEmpty());assertEquals("1",first.nextAfterId());
   var second=service.inspect(scope,1,1);assertFalse(second.hasMore());assertEquals(1,second.differences().size());
   assertTrue(second.differences().getFirst().reasons().contains("BALANCE_MISMATCH"));
   assertTrue(second.differences().getFirst().reasons().contains("BATCH_TOTAL_MISMATCH"));
   db.update("UPDATE o_goods_inventory_batch SET merchant_id=8 WHERE id=1");
   var foreign=service.inspect(scope,1,1).differences().getFirst();assertNull(foreign.batchQuantity());assertTrue(foreign.reasons().contains("BATCH_SCOPE_MISMATCH"));
   db.update("INSERT INTO o_goods_inventory_batch VALUES(3,999,9,1,7,10,1,1),(4,998,9,2,8,11,1,1)");
   var orphan=service.orphanBatches(scope,0,200);assertEquals(java.util.List.of("3"),orphan.batchIds());
   assertThrows(RuntimeException.class,()->service.inspect(new Scope("SUPPLIER",1L,null,null),0,10));
   assertThrows(RuntimeException.class,()->service.inspect(scope,0,201));
  } catch(java.sql.SQLException error){throw new RuntimeException(error);}
 }

 /** 库存有数量但完全没有批次记录时，应报「缺批次」，不能把批次合计当 0 报数额不符。 */
 @Test void missingBatchRecordIsNotReportedAsTotalMismatch() {
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:reconcile_no_batch;MODE=MySQL;DB_CLOSE_DELAY=-1");
  var db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE o_goods_inventory(id BIGINT,sku_id BIGINT,warehouse_id BIGINT,merchant_id BIGINT,shop_id BIGINT,stock_status INT,quantity INT,locked_quantity INT,available_quantity INT,is_delete INT)");
  db.execute("CREATE TABLE o_goods_inventory_batch(id BIGINT,inventory_id BIGINT,sku_id BIGINT,warehouse_id BIGINT,merchant_id BIGINT,shop_id BIGINT,stock_status INT,current_qty INT)");
  db.update("INSERT INTO o_goods_inventory VALUES(1,1,1,7,10,1,120,20,100,0)");
  var service=new InventoryReconciliationService(db);var scope=new Scope("SHOP",10L,7L,10L);
  var difference=service.inspect(scope,0,10).differences().getFirst();
  assertTrue(difference.reasons().contains("NO_BATCH_RECORD"));
  assertFalse(difference.reasons().contains("BATCH_TOTAL_MISMATCH"));
  assertNull(difference.batchQuantity(),"缺批次时合计应为未知，不能返回 0 让调用方误判成本");
 }
}
