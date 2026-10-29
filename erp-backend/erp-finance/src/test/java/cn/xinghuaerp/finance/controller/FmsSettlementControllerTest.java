package cn.xinghuaerp.finance.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.finance.service.FmsOrderSettlementService;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.finance.controller.FmsSettlementController;
import cn.xinghuaerp.system.entity.SysUser;

class FmsSettlementControllerTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private void login(){
        var sysUser=new cn.xinghuaerp.system.entity.SysUser();sysUser.setUserName("tester");
        var user=new LoginUser();user.setUserId(7L);user.setUser(sysUser);user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
    }
    @Test void requiresDedicatedPermissionBeforeAnySettlement() {
        var settlement=mock(FmsOrderSettlementService.class);var permissions=mock(AiAgentPermissionService.class);
        var access=mock(cn.xinghuaerp.system.service.BusinessAccessService.class);
        var scope=new cn.xinghuaerp.system.service.SalesScope(7L,10L);
        when(access.salesScope(7L)).thenReturn(scope);
        var controller=new FmsSettlementController(settlement,permissions,access);
        login();
        when(permissions.allows(7L,"fms:settlement:execute")).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->controller.autoRun(new FmsSettlementController.Request(List.of("1"))));
        verifyNoInteractions(settlement);
        when(permissions.allows(7L,"fms:settlement:execute")).thenReturn(true);
        when(settlement.autoSettlement(any(),anyString(),any())).thenReturn(Map.of("successCount",1,"failCount",0,"failReasons",List.of()));
        var result=controller.autoRun(new FmsSettlementController.Request(List.of("1")));
        assertEquals(1,((Map<?,?>)result.get(AjaxResult.DATA_TAG)).get("successCount"));
        verify(settlement).autoSettlement(List.of("1"),"tester",scope);
    }
    @Test void rejectsEmptyOversizedAndMalformedOrderIds() {
        var settlement=mock(FmsOrderSettlementService.class);var permissions=mock(AiAgentPermissionService.class);
        var access=mock(cn.xinghuaerp.system.service.BusinessAccessService.class);
        var scope=new cn.xinghuaerp.system.service.SalesScope(7L,10L);
        when(access.salesScope(7L)).thenReturn(scope);
        var controller=new FmsSettlementController(settlement,permissions,access);
        login();
        when(permissions.allows(7L,"fms:settlement:execute")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->controller.autoRun(null));
        assertThrows(IllegalArgumentException.class,()->controller.autoRun(new FmsSettlementController.Request(List.of())));
        assertThrows(IllegalArgumentException.class,()->controller.autoRun(new FmsSettlementController.Request(List.of("abc"))));
        assertThrows(IllegalArgumentException.class,()->controller.autoRun(new FmsSettlementController.Request(List.of("-1"))));
        assertThrows(IllegalArgumentException.class,()->controller.autoRun(new FmsSettlementController.Request(java.util.stream.IntStream.rangeClosed(1,201).mapToObj(String::valueOf).toList())));
        verifyNoInteractions(settlement);
    }
}
