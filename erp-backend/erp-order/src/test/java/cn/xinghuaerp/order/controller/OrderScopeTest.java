package cn.xinghuaerp.order.controller;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import cn.xinghuaerp.order.request.OrderSearchRequest;
import cn.xinghuaerp.order.entity.OOrder;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.order.controller.OrderController;
class OrderScopeTest {
 @Test void forgedFiltersCannotExpandScope() {
  var request = new OrderSearchRequest(); request.setMerchantId(99L); request.setShopId(99L);
  OrderController.applyOrderScope(request, new Scope("SHOP", 10L, 7L, 10L));
  assertEquals  (7L, request.getMerchantId()); assertEquals(10L, request.getShopId());
  var order = new OOrder(); order.setMerchantId(7L); order.setShopId(10L);
  var scope = new Scope("SHOP", 10L, 7L, 10L);
  assertDoesNotThrow(() -> OrderController.checkOrderScope(order,scope));
  order.setShopId(11L); assertThrows(AccessDeniedException.class, () -> OrderController.checkOrderScope(order,scope));
  order.setShopId(10L); order.setMerchantId(8L); assertThrows(AccessDeniedException.class, () -> OrderController.checkOrderScope(order,scope));
  assertThrows(AccessDeniedException.class, () -> OrderController.applyOrderScope(request,new Scope("SUPPLIER", 1L,null,null)));
 }
}
