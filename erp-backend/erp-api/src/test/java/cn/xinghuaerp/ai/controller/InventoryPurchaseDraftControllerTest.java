package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.ai.service.AiPurchaseDraftService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.controller.InventoryPurchaseDraftController;

class InventoryPurchaseDraftControllerTest {
    final AiAgentPermissionService permissions=mock(AiAgentPermissionService.class);
    final BusinessAccessService business=mock(BusinessAccessService.class);
    final AiPurchaseDraftService drafts=mock(AiPurchaseDraftService.class);
    final InventoryPurchaseDraftController controller=new InventoryPurchaseDraftController(new PurchaseAccessService(business,permissions),permissions,drafts);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void login() {
        var user=new LoginUser(); user.setUserId(7L); user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
    }
    InventoryPurchaseDraftController.Request request(boolean confirmed) {
        return new InventoryPurchaseDraftController.Request("7e691b25-6644-44cc-94b5-b6251a6d4355",1L,2L,3L,new BigDecimal("1.00"),confirmed);
    }
    @Test void permissionSubjectAndConfirmationAreAllRequired() {
        login();
        assertThrows(AccessDeniedException.class,()->controller.create(request(true)));
        assertThrows(AccessDeniedException.class,()->controller.suppliers(1L));
        when(permissions.allows(7L,"purchase:order:create")).thenReturn(true);
        when(business.scope(7L)).thenReturn(new Scope("SHOP",10L,7L,10L));
        assertThrows(AccessDeniedException.class,()->controller.create(request(true)));
        when(business.scope(7L)).thenReturn(new Scope("HEADQUARTERS",null,null,null));
        assertThrows(AccessDeniedException.class,()->controller.create(request(true)));
        when(permissions.allows(7L,"ai:inventory:query")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->controller.create(request(false)));
        verifyNoInteractions(drafts);
    }
    @Test void recentDraftsRequireQueryPermissionAndUseCurrentUser() {
        login();
        assertThrows(AccessDeniedException.class, controller::recent);
        when(permissions.allows(7L,"purchase:order:query")).thenReturn(true);
        when(business.scope(7L)).thenReturn(new Scope("SHOP",10L,7L,10L));
        assertThrows(AccessDeniedException.class, controller::recent);
        when(business.scope(7L)).thenReturn(new Scope("HEADQUARTERS",null,null,null));
        assertThrows(AccessDeniedException.class, controller::recent);
        verifyNoInteractions(drafts);
        when(permissions.allows(7L,"ai:inventory:query")).thenReturn(true);
        controller.recent();
        verify(drafts).recent(7L);
    }
}
