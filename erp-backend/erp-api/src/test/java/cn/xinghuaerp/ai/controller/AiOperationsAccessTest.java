package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.ai.service.AiRequestAdmission;
import cn.xinghuaerp.order.facade.OrderNotificationOperations;
import cn.xinghuaerp.system.service.*;
import cn.xinghuaerp.system.security.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiOperationsAccessTest {
    @Test void operationsRequireHeadquartersAndConfigPermissionBeforeAnyReadOrWrite() {
        var user=new LoginUser();user.setUserId(7L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,java.util.List.of()));
        try {
            var access=mock(BusinessAccessService.class);var permissions=mock(AiAgentPermissionService.class);var jdbc=mock(JdbcTemplate.class);var notifications=mock(OrderNotificationOperations.class);var admission=mock(AiRequestAdmission.class);
            var controller=new AiOperationsController(access,permissions,jdbc,notifications,admission);
            assertThrows(AccessDeniedException.class,controller::summary);
            assertThrows(AccessDeniedException.class,()->controller.enabled(new AiOperationsController.EnabledRequest(false)));
            verifyNoInteractions(jdbc,notifications,admission);
            when(permissions.allows(7L,"ai:config:list")).thenReturn(true);
            controller.enabled(new AiOperationsController.EnabledRequest(false));verify(admission).setEnabled(false);
            doThrow(new AccessDeniedException("not HQ")).when(access).requireHeadquarters(7L);
            assertThrows(AccessDeniedException.class,controller::summary);verifyNoInteractions(jdbc,notifications);
        } finally {SecurityContextHolder.clearContext();}
    }
}
