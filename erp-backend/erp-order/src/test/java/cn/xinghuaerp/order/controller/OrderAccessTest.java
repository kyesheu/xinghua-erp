package cn.xinghuaerp.order.controller;

import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.order.bo.OrderItemSpecIdUpdateBo;
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
class OrderAccessTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void nonHeadquartersCannotReachOrderServicesEvenWithForgedIds() {
        var user = new LoginUser(); user.setUserId(7L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        var access = mock(BusinessAccessService.class);
        doThrow(new AccessDeniedException("denied")).when(access).requireHeadquarters(7L);
        when(access.scope(7L)).thenReturn(new BusinessSubjectScopeService.Scope("SUPPLIER", 7L, null, null));
        var orders = mock(OOrderService.class); var items = mock(OOrderItemService.class);
                var controller = new OrderController(orders, items, access);
        var itemController = new OrderItemController(items, orders, access);
        assertThrows(AccessDeniedException.class, () -> controller.list(null, null));
        assertThrows(AccessDeniedException.class, () -> controller.getInfo(999L));
        assertThrows(AccessDeniedException.class, () -> controller.wait_send_orderlist(null, null));
        assertThrows(AccessDeniedException.class, () -> controller.wait_send_orderItemList(null, null));
        assertThrows(AccessDeniedException.class, () -> itemController.list(null, null));
        assertThrows(AccessDeniedException.class, () -> itemController.orderItemSpecIdUpdate(new OrderItemSpecIdUpdateBo()));
        verifyNoInteractions(orders, items);
    }
}
