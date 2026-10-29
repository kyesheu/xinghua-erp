package cn.xinghuaerp.finance.service;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import static org.junit.jupiter.api.Assertions.*;
class SettlementReconciliationTest {
 @Test void scansNormalRowsThenReportsDifferencesWithoutCrossingShopScope() {
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:finance_reconciliation;MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE fms_order_settlement(id BIGINT,merchant_id BIGINT,shop_id BIGINT,sales_amount DECIMAL(12,2),purchase_cost DECIMAL(12,2),shipping_fee DECIMAL(12,2),platform_fee DECIMAL(12,2),marketing_fee DECIMAL(12,2),other_fee DECIMAL(12,2),total_cost DECIMAL(12,2),profit DECIMAL(12,2))");
  db.execute("CREATE TABLE fms_order_settlement_item(settlement_id BIGINT,amount DECIMAL(12,2))");
  db.update("INSERT INTO fms_order_settlement VALUES(1,7,10,100,0,0,0,0,0,0,100),(2,7,10,100,10,0,0,0,0,20,90),(3,8,11,100,0,0,0,0,0,0,0)");
  db.update("INSERT INTO fms_order_settlement_item VALUES(2,10)");
  var service=new SettlementReconciliationService(db);var scope=new Scope("SHOP",10L,7L,10L);
  var first=service.inspect(scope,0,1);assertTrue(first.hasMore());assertTrue(first.differences().isEmpty());assertEquals("1",first.nextAfterId());
  var next=service.inspect(scope,1,1);assertFalse(next.hasMore());assertEquals(1,next.differences().size());
  assertTrue(next.differences().getFirst().reasons().containsAll(java.util.List.of("COST_COMPONENT_MISMATCH","PROFIT_MISMATCH","DETAIL_TOTAL_MISMATCH")));
  db.update("UPDATE fms_order_settlement SET sales_amount=NULL WHERE id=2");assertTrue(service.inspect(scope,1,1).differences().getFirst().reasons().contains("UNKNOWN_AMOUNT"));
  assertThrows(RuntimeException.class,()->service.inspect(new Scope("WAREHOUSE",1L,null,null),0,10));
  assertThrows(IllegalArgumentException.class,()->service.inspect(scope,0,201));db.execute("SHUTDOWN");
 }
}
