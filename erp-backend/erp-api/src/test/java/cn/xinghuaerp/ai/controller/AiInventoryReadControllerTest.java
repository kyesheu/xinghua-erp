package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.ai.service.AiInventoryReadService;
import cn.xinghuaerp.system.service.ISysMenuService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.controller.AiInventoryReadController;

class AiInventoryReadControllerTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void permissionComesFromCurrentRoleMenus() {
        var menus = mock(ISysMenuService.class);
        var permissions = new AiAgentPermissionService(menus);
        when(menus.selectMenuPermsByUserId(7L)).thenReturn(Set.of("ai:inventory:query"));
        assertTrue(permissions.allows(7L,"ai:inventory:query"));
        assertFalse(permissions.allows(7L,"ai:purchase:query"));
        assertTrue(permissions.allows(1L,"ai:inventory:query"));
        assertFalse(permissions.allows(null,"ai:inventory:query"));
    }
    @Test void overviewRejectsCachedWildcardWithoutCurrentPermission() {
        var permissions=mock(AiAgentPermissionService.class);
        var inventory=mock(AiInventoryReadService.class);
        var access=mock(BusinessAccessService.class);
        var user=new LoginUser(); user.setUserId(7L); user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
        var controller=new AiInventoryReadController(inventory,access,permissions);
        assertThrows(AccessDeniedException.class,()->controller.overview(1,50));
        assertThrows(AccessDeniedException.class,()->controller.dossier(5L));
        verifyNoInteractions(inventory,access);
    }
    @Test void overviewOnlyIncludesSalesWhenCurrentPermissionAllowsIt() {
        var permissions = mock(AiAgentPermissionService.class);
        var inventory = mock(AiInventoryReadService.class);
        var access = mock(BusinessAccessService.class);
        var user = new LoginUser(); user.setUserId(7L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
        var scope = new Scope("SHOP",10L,7L,10L);
        when(access.scope(7L)).thenReturn(scope);
        when(permissions.allows(7L,"ai:inventory:query")).thenReturn(true);
        var controller = new AiInventoryReadController(inventory,access,permissions);
        controller.overview(1,50);
        verify(inventory).overview(1,50,scope,false,false);
        controller.dossier(5L);
        verify(inventory).dossier(5L,scope,false,false);
        when(permissions.allows(7L,"ai:sales:query")).thenReturn(true);
        when(permissions.allows(7L,"ai:purchase:query")).thenReturn(true);
        controller.overview(1,50);
        verify(inventory).overview(1,50,scope,true,true);
        controller.dossier(5L);
        verify(inventory).dossier(5L,scope,true,true);
    }
}
