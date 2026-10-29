package cn.xinghuaerp.finance.service;
import cn.xinghuaerp.finance.service.impl.FmsOrderSettlementServiceImpl;
import cn.xinghuaerp.finance.entity.*;
import cn.xinghuaerp.finance.mapper.*;
import cn.xinghuaerp.order.facade.OrderFacade;
import cn.xinghuaerp.order.entity.OOrder;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SettlementTransactionTest {
 static void inject(Object target,String name,Object value)throws Exception {
  Class<?> type=target.getClass();while(type!=null){try{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;}catch(NoSuchFieldException missing){type=type.getSuperclass();}}
  throw new IllegalArgumentException(name);
 }
 @Test void laterWriteFailureRollsBackInsertedHeaderAndDoesNotClaimSuccess() throws Exception {
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:settlement_transaction;MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE settlement_probe(id BIGINT PRIMARY KEY)");
  var service=new FmsOrderSettlementServiceImpl(){public FmsOrderSettlement calculateOrderSettlement(String id){
   var row=new FmsOrderSettlement();row.setOrderId(Long.valueOf(id));row.setPurchaseCost(BigDecimal.ONE);row.setShippingFee(BigDecimal.ZERO);row.setPlatformFee(BigDecimal.ZERO);row.setMarketingFee(BigDecimal.ZERO);row.setOtherFee(BigDecimal.ZERO);return row;
  }};
  var orders=mock(OrderFacade.class);var order=new OOrder();order.setSettlementStatus(0);when(orders.getOOrderOne(any())).thenReturn(order);
  var headers=mock(FmsOrderSettlementMapper.class);when(headers.insert(any(FmsOrderSettlement.class))).thenAnswer(call->{FmsOrderSettlement row=call.getArgument(0);row.setId(row.getOrderId());db.update("INSERT INTO settlement_probe VALUES(?)",row.getId());return 1;});
  var items=mock(FmsOrderSettlementItemMapper.class);when(items.insert(any(FmsOrderSettlementItem.class))).thenThrow(new IllegalStateException("simulated detail failure"));
  inject(service,"baseMapper",headers);inject(service,"settlementItemMapper",items);inject(service,"orderMapper",orders);inject(service,"transactionManager",new DataSourceTransactionManager(ds));
  assertEquals(false,service.manualSettlement("1",null,"tester").get("success"));
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM settlement_probe",Integer.class));
  var batch=service.autoSettlement(java.util.List.of("2","3"),"tester");assertEquals(0,batch.get("successCount"));assertEquals(2,batch.get("failCount"));
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM settlement_probe",Integer.class));
  order.setSettlementStatus(1);clearInvocations(headers);service.manualSettlement("4",null,"tester");verifyNoInteractions(headers);
  db.execute("SHUTDOWN");
 }
 @Test void cancellationClearsOrderAndExpenseLinksExplicitly() throws Exception {
  var service=new FmsOrderSettlementServiceImpl();var headers=mock(FmsOrderSettlementMapper.class);var items=mock(FmsOrderSettlementItemMapper.class);
  var expenses=mock(FmsExpenseMapper.class);var expenseItems=mock(FmsExpenseItemMapper.class);var orders=mock(OrderFacade.class);
  var settlement=new FmsOrderSettlement();settlement.setId(8L);settlement.setOrderId(1L);
  when(headers.selectById(8L)).thenReturn(settlement);when(headers.selectOne(any())).thenReturn(settlement);when(headers.deleteById(8L)).thenReturn(1);
  var order=new OOrder();order.setSettlementId(8L);when(orders.getOOrderOne(any())).thenReturn(order);when(orders.clearOrderSettlement(1L,8L)).thenReturn(1);
  var expenseItem=new FmsExpenseItem();expenseItem.setExpenseId(9L);when(expenseItems.selectList(any())).thenReturn(java.util.List.of(expenseItem));
  when(expenses.update(isNull(),any())).thenAnswer(call->{
   com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FmsExpense> wrapper=call.getArgument(1);
   assertTrue(wrapper.getSqlSet().contains("settlement_id="));assertTrue(wrapper.getSqlSet().contains("settlement_time="));
   assertTrue(wrapper.getParamNameValuePairs().containsValue(null));return 1;
  });
  inject(service,"baseMapper",headers);inject(service,"settlementItemMapper",items);inject(service,"orderMapper",orders);inject(service,"expenseMapper",expenses);inject(service,"expenseItemMapper",expenseItems);
  assertTrue(service.cancelSettlement(8L,"tester"));verify(orders).clearOrderSettlement(1L,8L);verify(expenses).update(isNull(),any());
 }
 @Test void negativeAdjustmentIsPreservedInSettlementDetails() throws Exception {
  var service=new FmsOrderSettlementServiceImpl();var mapper=mock(FmsOrderSettlementItemMapper.class);when(mapper.insert(any(FmsOrderSettlementItem.class))).thenReturn(1);inject(service,"settlementItemMapper",mapper);
  var row=new FmsOrderSettlement();row.setId(1L);row.setPurchaseCost(BigDecimal.ZERO);row.setShippingFee(BigDecimal.ZERO);row.setPlatformFee(BigDecimal.ZERO);row.setMarketingFee(BigDecimal.ZERO);row.setOtherFee(new BigDecimal("-12.50"));
  var save=FmsOrderSettlementServiceImpl.class.getDeclaredMethod("saveSettlementItems",FmsOrderSettlement.class);save.setAccessible(true);save.invoke(service,row);
  var captured=org.mockito.ArgumentCaptor.forClass(FmsOrderSettlementItem.class);verify(mapper).insert(captured.capture());assertEquals(new BigDecimal("-12.50"),captured.getValue().getAmount());
 }
 @Test void unapprovedExpenseIsNotMarkedSettled() throws Exception {
  var service=new FmsOrderSettlementServiceImpl();var details=mock(FmsExpenseItemMapper.class);var headers=mock(FmsExpenseMapper.class);
  var included=new FmsExpenseItem();included.setExpenseId(1L);var pending=new FmsExpenseItem();pending.setExpenseId(2L);
  when(details.selectList(any())).thenReturn(java.util.List.of(included,pending));when(details.updateById(included)).thenReturn(1);
  var approved=new FmsExpense();approved.setId(1L);approved.setStatus(5);when(headers.selectList(any())).thenReturn(java.util.List.of(approved));
  inject(service,"expenseItemMapper",details);inject(service,"expenseMapper",headers);
  var update=FmsOrderSettlementServiceImpl.class.getDeclaredMethod("updateExpenseItemSettlementStatus",Long.class,Long.class);update.setAccessible(true);update.invoke(service,1L,8L);
  verify(details).updateById(included);verify(details,never()).updateById(pending);assertNull(pending.getSettlementId());
 }
 @Test void foreignOrderCannotBeSettledEvenWithExecutionPermission() throws Exception {
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:foreign_settlement;MODE=MySQL");
  var service=new FmsOrderSettlementServiceImpl();var orders=mock(OrderFacade.class);var headers=mock(FmsOrderSettlementMapper.class);
  var order=new OOrder();order.setMerchantId(99L);order.setShopId(88L);order.setSettlementStatus(0);when(orders.getOOrderOne(any())).thenReturn(order);
  inject(service,"orderMapper",orders);inject(service,"baseMapper",headers);inject(service,"transactionManager",new DataSourceTransactionManager(ds));
  var result=service.autoSettlement(java.util.List.of("1"),"tester",new cn.xinghuaerp.system.service.SalesScope(7L,10L));
  assertEquals(0,result.get("successCount"));assertEquals(1,result.get("failCount"));verifyNoInteractions(headers);
 }
 @Test void missingBatchCostIsNotSilentlyTreatedAsZero() throws Exception {
  var service=new FmsOrderSettlementServiceImpl();var inventory=mock(cn.xinghuaerp.inventory.facade.InventoryFacade.class);
  var batch=new cn.xinghuaerp.inventory.entity.OOrderStockingItemBatch();batch.setQuantity(1);
  when(inventory.listStockingBatches(any())).thenReturn(java.util.List.of(batch));inject(service,"itemBatchMapper",inventory);
  var calculate=FmsOrderSettlementServiceImpl.class.getDeclaredMethod("calculatePurchaseCost",String.class);calculate.setAccessible(true);
  var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->calculate.invoke(service,"1"));assertInstanceOf(IllegalStateException.class,error.getCause());
  batch.setUnitCost(BigDecimal.ZERO);assertEquals(BigDecimal.ZERO,calculate.invoke(service,"1"));
 }
 @Test void supplierDispatchWithoutCostSourceCannotBecomeZeroCostProfit() throws Exception {
  var service=new FmsOrderSettlementServiceImpl();var inventory=mock(cn.xinghuaerp.inventory.facade.InventoryFacade.class);
  var task=new cn.xinghuaerp.inventory.entity.OOrderStocking();task.setType(300);task.setShippingCost(BigDecimal.ZERO);
  when(inventory.listOOrderStocking(any())).thenReturn(java.util.List.of(task));inject(service,"orderStockingMapper",inventory);
  var calculate=FmsOrderSettlementServiceImpl.class.getDeclaredMethod("calculateShippingFee",String.class);calculate.setAccessible(true);
  assertThrows(java.lang.reflect.InvocationTargetException.class,()->calculate.invoke(service,"1"));
  task.setType(0);assertEquals(BigDecimal.ZERO,calculate.invoke(service,"1"));
 }
}
