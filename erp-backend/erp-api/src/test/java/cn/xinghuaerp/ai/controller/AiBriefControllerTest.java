package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.ai.service.impl.AiBriefService;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.controller.AiBriefController;

/** AI 简报为全平台汇总数据且未做主体过滤，只能由总部主体访问。 */
class AiBriefControllerTest {
    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private void login(long userId) {
        var user = new LoginUser();
        user.setUserId(userId);
        user.setPermissions(java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void nonHeadquartersSubjectsAreDeniedBeforeGeneratingBrief() {
        var briefService = mock(AiBriefService.class);
        var access = mock(BusinessAccessService.class);
        login(7L);
        when(access.scope(7L)).thenReturn(new Scope("MERCHANT", 101L, 101L, null));
        var controller = new AiBriefController(briefService, access);
        assertThrows(AccessDeniedException.class, controller::getBrief);
        when(access.scope(7L)).thenReturn(new Scope("SHOP", 110L, 101L, 110L));
        assertThrows(AccessDeniedException.class, controller::getBrief);
        verifyNoInteractions(briefService);
    }

    @Test
    void headquartersCanGenerateBrief() {
        var briefService = mock(AiBriefService.class);
        var access = mock(BusinessAccessService.class);
        login(7L);
        when(access.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        var controller = new AiBriefController(briefService, access);
        controller.getBrief();
        verify(briefService).generateBrief();
    }
}
