package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.ai.service.impl.*;
import cn.xinghuaerp.order.service.OOrderService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiFallbackSafetyTest {
    @Test void nonHeadquartersFallbackDoesNotReadGlobalFacts() {
        var orders=mock(OOrderService.class);var inventory=mock(InventoryTools.class);var refunds=mock(RefundTools.class);var access=mock(BusinessAccessService.class);
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(access).requireHeadquarters(7L);
        var result=new AiAnalysisFallback(orders,inventory,refunds,access,mock(cn.xinghuaerp.system.service.AiAgentPermissionService.class)).build("sales",7L);
        assertTrue(result.contains("有权限的业务页面"));verifyNoInteractions(orders,inventory,refunds);
    }
    @Test void failedFactsAreNotPresentedAsZeroOrNoOrders() {
        var orders=mock(OOrderService.class);when(orders.getTodaySalesDaily(null)).thenThrow(new IllegalStateException("offline"));
        when(orders.getWaitShipOrderAllCount(null)).thenThrow(new IllegalStateException("offline"));
        when(orders.salesDaily()).thenThrow(new IllegalStateException("offline"));
        var permissions=mock(cn.xinghuaerp.system.service.AiAgentPermissionService.class);
        when(permissions.allows(anyLong(),anyString())).thenReturn(true);
        var fallback=new AiAnalysisFallback(orders,mock(InventoryTools.class),mock(RefundTools.class),mock(BusinessAccessService.class),permissions);
        var result=fallback.build("operation",7L);
        assertTrue(result.contains("不可用"));assertFalse(result.contains("当前没有待发货订单"));
        assertTrue(fallback.build("sales",7L).contains("不代表没有销售"));
    }
    @Test void headquartersWithoutFactPermissionsDoesNotGainAccessThroughFallback() {
        var orders=mock(OOrderService.class);var inventory=mock(InventoryTools.class);var refunds=mock(RefundTools.class);
        var fallback=new AiAnalysisFallback(orders,inventory,refunds,mock(BusinessAccessService.class),mock(cn.xinghuaerp.system.service.AiAgentPermissionService.class));
        assertTrue(fallback.build("sales",7L).contains("无销售资料权限"));
        fallback.build("inventory",7L);fallback.build("customer",7L);
        verifyNoInteractions(orders,inventory,refunds);
    }
}
