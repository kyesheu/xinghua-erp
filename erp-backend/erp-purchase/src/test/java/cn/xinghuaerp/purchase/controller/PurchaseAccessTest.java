package cn.xinghuaerp.purchase.controller;

import cn.xinghuaerp.purchase.request.ConfirmReceiptRequest;
import cn.xinghuaerp.purchase.request.PurchaseOrderAddRequest;
import cn.xinghuaerp.purchase.request.PurchaseOrderOptionRequest;
import cn.xinghuaerp.purchase.request.PurchaseOrderStockInBo;
import cn.xinghuaerp.purchase.request.SearchRequest;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrderItem;
import cn.xinghuaerp.purchase.entity.ErpPurchaseLogistics;
import cn.xinghuaerp.purchase.service.ErpPurchaseLogisticsService;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import cn.xinghuaerp.purchase.service.ErpPurchaseOrderItemService;
import cn.xinghuaerp.purchase.service.ErpPurchaseOrderService;
import cn.xinghuaerp.purchase.service.ErpPurchaseOrderShipService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.purchase.controller.PurchaseLogisticsController;
import cn.xinghuaerp.purchase.controller.PurchaseOrderController;
import cn.xinghuaerp.purchase.controller.PurchaseOrderShipController;

class PurchaseAccessTest {
    final AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
    final BusinessAccessService business = mock(BusinessAccessService.class);
    final PurchaseAccessService access = new PurchaseAccessService(business, permissions);
    final ErpPurchaseOrderService orders = mock(ErpPurchaseOrderService.class);
    final ErpPurchaseOrderItemService items = mock(ErpPurchaseOrderItemService.class);
    final ErpPurchaseOrderShipService ships = mock(ErpPurchaseOrderShipService.class);
    final PurchaseOrderController orderController = new PurchaseOrderController(orders, items, access);
    final PurchaseOrderShipController shipController = new PurchaseOrderShipController(ships, access);

    final ErpPurchaseLogisticsService carriers = mock(ErpPurchaseLogisticsService.class);
    final PurchaseLogisticsController carrierController = new PurchaseLogisticsController(carriers, access);

    void login() {
        var user = new LoginUser(); user.setUserId(7L); user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void everyLegacyEndpointChecksLivePermissionBeforeAccessingData() {
        login();
        assertThrows(AccessDeniedException.class, () -> orderController.itemList(new ErpPurchaseOrderItem(), new PageQuery()));
        assertThrows(AccessDeniedException.class, () -> orderController.list(new SearchRequest(), new PageQuery()));
        assertThrows(AccessDeniedException.class, () -> orderController.getInfo(1L));
        assertThrows(AccessDeniedException.class, () -> orderController.add(new PurchaseOrderAddRequest(), null));
        assertThrows(AccessDeniedException.class, () -> orderController.updateStatus(new PurchaseOrderOptionRequest(), null));
        assertThrows(AccessDeniedException.class, () -> shipController.shipList(new SearchRequest(), new PageQuery()));
        assertThrows(AccessDeniedException.class, () -> shipController.getInfo(1L));
        assertThrows(AccessDeniedException.class, () -> shipController.confirmReceipt(new ConfirmReceiptRequest(), null));
        assertThrows(AccessDeniedException.class, () -> shipController.createStockInEntry(new PurchaseOrderStockInBo()));
        verifyNoInteractions(orders, items, ships, business);
    }

    @Test void nonHeadquartersCannotUseLegacyPurchaseEvenWithPermission() {
        login();
        when(permissions.allows(7L, "purchase:order:query")).thenReturn(true);
        when(business.scope(7L)).thenReturn(new Scope("SHOP", 10L, 7L, 10L));
        assertThrows(AccessDeniedException.class, () -> orderController.getInfo(1L));
        verifyNoInteractions(orders);
        when(business.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        orderController.getInfo(1L);
        verify(orders).getDetailById(1L);
    }

    @Test void carrierEndpointsDenyMissingLivePermissionBeforeAnyServiceCall() {
        login();
        assertThrows(AccessDeniedException.class, () -> carrierController.list(null, null, 1, new PageQuery()));
        assertThrows(AccessDeniedException.class, () -> carrierController.getInfo(1L));
        assertThrows(AccessDeniedException.class, () -> carrierController.add(new ErpPurchaseLogistics()));
        assertThrows(AccessDeniedException.class, () -> carrierController.edit(new ErpPurchaseLogistics()));
        assertThrows(AccessDeniedException.class, () -> carrierController.logisticsUpdateStatus(new ErpPurchaseLogistics()));
        assertThrows(AccessDeniedException.class, () -> carrierController.remove(new Long[]{1L,2L}));
        verifyNoInteractions(carriers, business);
    }

    @Test void carrierQueryPermissionDoesNotAllowMaintenance() {
        login();
        when(permissions.allows(7L, "purchase:order:query")).thenReturn(true);
        when(business.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        carrierController.list(null, null, 1, new PageQuery()); carrierController.getInfo(1L);
        verify(carriers).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(carriers).getById(1L);
        assertThrows(AccessDeniedException.class, () -> carrierController.add(new ErpPurchaseLogistics()));
        assertThrows(AccessDeniedException.class, () -> carrierController.edit(new ErpPurchaseLogistics()));
        assertThrows(AccessDeniedException.class, () -> carrierController.logisticsUpdateStatus(new ErpPurchaseLogistics()));
        assertThrows(AccessDeniedException.class, () -> carrierController.remove(new Long[]{1L}));
        verifyNoMoreInteractions(carriers);
    }

    @Test void carrierPermissionCannotOverrideBusinessSubjectAndRevocationIsImmediate() {
        login();
        when(permissions.allows(7L, "purchase:order:query")).thenReturn(true);
        when(permissions.allows(7L, "purchase:logistics:manage")).thenReturn(true);
        for (String type : List.of("MERCHANT","SHOP","SUPPLIER","WAREHOUSE")) {
            when(business.scope(7L)).thenReturn(new Scope(type, 10L, 7L, 10L));
            assertThrows(AccessDeniedException.class, () -> carrierController.list(null, null, 1, new PageQuery()));
            assertThrows(AccessDeniedException.class, () -> carrierController.getInfo(1L));
            assertThrows(AccessDeniedException.class, () -> carrierController.add(new ErpPurchaseLogistics()));
            assertThrows(AccessDeniedException.class, () -> carrierController.edit(new ErpPurchaseLogistics()));
            assertThrows(AccessDeniedException.class, () -> carrierController.logisticsUpdateStatus(new ErpPurchaseLogistics()));
            assertThrows(AccessDeniedException.class, () -> carrierController.remove(new Long[]{1L,2L}));
        }
        verifyNoInteractions(carriers);
        when(business.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        when(carriers.getById(1L)).thenReturn(new ErpPurchaseLogistics());
        var carrier = new ErpPurchaseLogistics();
        carrier.setName("顺丰速运"); carrier.setCode("SF"); carrier.setMerchantId(99L); carrier.setShopId(88L);
        carrierController.add(carrier);
        assertEquals("顺丰速运", carrier.getName());
        assertEquals(0L,carrier.getMerchantId()); assertEquals(0L,carrier.getShopId());
        verify(carriers).existsByCode("SF", null);
        verify(carriers).save(carrier);
        carrier.setId(1L);
        carrierController.edit(carrier);
        verify(carriers).getById(1L); verify(carriers).existsByCode("SF", 1L);
        verify(carriers).updateById(carrier);
        carrier.setStatus(0);
        carrierController.logisticsUpdateStatus(carrier); verify(carriers).updateStatus(1L,1);
        carrierController.remove(new Long[]{1L,2L}); verify(carriers).removeBatchByIds(List.of(1L,2L));
        when(permissions.allows(7L, "purchase:logistics:manage")).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> carrierController.remove(new Long[]{3L}));
        verifyNoMoreInteractions(carriers);
    }

    @Test void auditRequiresItsOwnPermission() {
        login();
        when(permissions.allows(7L, "purchase:order:operate")).thenReturn(true);
        when(business.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        var request = new PurchaseOrderOptionRequest(); request.setOptionType("audit");
        assertThrows(AccessDeniedException.class, () -> orderController.updateStatus(request, null));
        verifyNoInteractions(orders);
    }
}
