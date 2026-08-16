package cn.xinghuaerp.order.controller;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.query.OrderItemQuery;
import cn.xinghuaerp.order.request.OrderSearchRequest;
import cn.xinghuaerp.system.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;
import cn.xinghuaerp.order.service.OOrderItemService;
import cn.xinghuaerp.order.service.OOrderService;
import cn.xinghuaerp.order.controller.OrderController;
import cn.xinghuaerp.order.controller.OrderItemController;
class OrderControllerScopeTest {
 @AfterEach void clear() { SecurityContextHolder.clearContext(); }
 @Test void shopFiltersAreAppliedToEveryListAndCrossShopDetailRejected() {
  var user=new LoginUser(); user.setUserId(7L);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
  var access=mock(BusinessAccessService.class); when(access.scope(7L)).thenReturn(new BusinessSubjectScopeService.Scope("SHOP",10L,7L,10L));
  var orders=mock(OOrderService.class); var items=mock(OOrderItemService.class);
  when(orders.queryPageList(any(),any())).thenReturn(PageResult.build(List.of()));
  when(orders.queryWaitDistOrderPageList(any(),any())).thenReturn(PageResult.build(List.of()));
  when(items.queryWaitDistOrderItemPageList(any(),any())).thenReturn(PageResult.build(List.of()));
  when(items.selectPageVo(any(),any())).thenReturn(PageResult.build(List.of()));
  var controller=new OrderController(orders,items,access);
  var itemController=new OrderItemController(items,orders,access);
  var request=new OrderSearchRequest(); request.setMerchantId(8L); request.setShopId(11L);
  controller.list(request,new PageQuery()); assertEquals(7L,request.getMerchantId()); assertEquals(10L,request.getShopId());
  request.setMerchantId(8L); request.setShopId(11L); controller.wait_send_orderlist(request,new PageQuery());
  assertEquals(7L,request.getMerchantId()); assertEquals(10L,request.getShopId());
  request.setMerchantId(8L); request.setShopId(11L); controller.wait_send_orderItemList(request,new PageQuery());
  assertEquals(7L,request.getMerchantId()); assertEquals(10L,request.getShopId());
  var itemQuery=new OrderItemQuery(); itemQuery.setMerchantId(8L); itemQuery.setShopId(11L);
  itemController.list(itemQuery,new PageQuery()); assertEquals(7L,itemQuery.getMerchantId()); assertEquals(10L,itemQuery.getShopId());
  var other=new OOrder(); other.setMerchantId(7L); other.setShopId(11L); when(orders.queryDetailById(99L)).thenReturn(other);
  assertThrows(AccessDeniedException.class,()->controller.getInfo(99L));
 }
}
