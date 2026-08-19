package cn.xinghuaerp.fulfillment.controller;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.inventory.service.OOrderStockingService;
import cn.xinghuaerp.purchase.service.SupplierDispatchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.fulfillment.controller.ShipOrderCloudWarehouseController;
import cn.xinghuaerp.fulfillment.controller.ShipOrderItemPushController;
class CloudWarehouseDisabledTest {
 @AfterEach void clear() { SecurityContextHolder.clearContext(); }
 @Test void neitherEntryCreatesStockingTasks() {
  var user = new LoginUser(); user.setUserId(1L);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
  var access = mock(BusinessAccessService.class); var stock = mock(OOrderStockingService.class); var dispatch = mock(SupplierDispatchService.class);
  var orders = new ShipOrderCloudWarehouseController(stock, access); var items = new ShipOrderItemPushController(stock, dispatch, access);
  assertEquals(2001, orders.pushToCloudWarehouse(Map.of("ids", List.of(11L))).get("code"));
  assertEquals(2001, items.pushOrderItemToCloudWarehouse(Map.of("ids", List.of(22L))).get("code"));
  verify(access, times(2)).requireHeadquarters(1L); verifyNoInteractions(stock, dispatch);
 }
}

