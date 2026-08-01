package cn.xinghuaerp.inventory.service;

import cn.xinghuaerp.inventory.entity.ErpStockIn;
import cn.xinghuaerp.inventory.entity.ErpStockOut;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.inventory.service.WarehouseAccessService;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;

class WarehouseAccessServiceTest {
    @Test void resolvesPermissionsBeforeScope() {
        var business=mock(BusinessAccessService.class); var permissions=mock(AiAgentPermissionService.class);
        var service=new WarehouseAccessService(business,permissions);
        assertThrows(AccessDeniedException.class, () -> service.scope(7L,"wms:stockin:query"));
        verifyNoInteractions(business);
        when(permissions.allows(7L,"wms:stockin:query")).thenReturn(true);
        when(business.scope(7L)).thenReturn(new Scope("SHOP",10L,7L,10L));
        assertEquals("SHOP",service.scope(7L,"wms:stockin:query").type());
    }
    @Test void queryAndEntryScopeCannotBeForged() {
        var shop=new Scope("SHOP",10L,7L,10L);
        var inbound=new ErpStockIn(); inbound.setMerchantId(99L); inbound.setShopId(99L); inbound.setWarehouseId(1L);
        WarehouseAccessService.apply(shop,inbound); assertEquals(7L,inbound.getMerchantId()); assertEquals(10L,inbound.getShopId());
        WarehouseAccessService.check(shop,inbound); inbound.setShopId(11L);
        assertThrows(AccessDeniedException.class, () -> WarehouseAccessService.check(shop,inbound));
        var outbound=new ErpStockOut(); outbound.setWarehouseId(3L); outbound.setMerchantId(99L); outbound.setShopId(99L);
        WarehouseAccessService.check(new Scope("WAREHOUSE",3L,null,null),outbound);
        assertThrows(AccessDeniedException.class, () -> WarehouseAccessService.check(shop,outbound));
        assertThrows(AccessDeniedException.class, () -> WarehouseAccessService.validate(new Scope("SUPPLIER",5L,null,null)));
        assertThrows(AccessDeniedException.class, () -> WarehouseAccessService.headquartersOnly(shop));
    }
}
