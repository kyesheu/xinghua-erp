package cn.xinghuaerp.inventory.controller;

import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import cn.xinghuaerp.inventory.service.OGoodsInventoryBatchService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.inventory.controller.GoodsInventoryController;

class GoodsInventoryScopeTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void inventoryDetailRequiresCurrentPermissionEvenWhenCachedUserHasWildcard() {
        var stock=mock(OGoodsInventoryService.class); var batches=mock(OGoodsInventoryBatchService.class);
        var access=mock(BusinessAccessService.class); var permissions=mock(AiAgentPermissionService.class);
        var user=new LoginUser(); user.setUserId(7L); user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
        var controller=new GoodsInventoryController(stock,batches,access,permissions);
        when(stock.getById(1L)).thenReturn(new OGoodsInventory());
        assertThrows(AccessDeniedException.class, () -> controller.getInfo(1L));
        verifyNoInteractions(access,batches);
    }
    @Test void userCannotChooseDifferentMerchantOrShop() {
        var item = new OGoodsInventory(); item.setMerchantId(999L); item.setShopId(88L); item.setWarehouseId(3L);
        var scope = new Scope("SHOP",10L,7L,10L);
        GoodsInventoryController.applyInventoryScope(item, scope);
        assertEquals(7L,item.getMerchantId()); assertEquals(10L,item.getShopId());
        GoodsInventoryController.checkInventory(scope,item);
        item.setShopId(11L);
        assertThrows(AccessDeniedException.class, () -> GoodsInventoryController.checkInventory(scope,item));
    }
    @Test void warehouseAndSupplierScopeAreEnforced() {
        var item = new OGoodsInventory(); item.setWarehouseId(999L);
        GoodsInventoryController.applyInventoryScope(item,new Scope("WAREHOUSE",3L,null,null));
        assertEquals(3L,item.getWarehouseId());
        assertThrows(AccessDeniedException.class, () -> GoodsInventoryController.applyInventoryScope(item,new Scope("SUPPLIER",7L,null,null)));
        assertThrows(AccessDeniedException.class, () -> GoodsInventoryController.applyInventoryScope(item,new Scope("MERCHANT",7L,null,null)));
    }
}
