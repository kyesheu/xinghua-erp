package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.ai.entity.AiConfig;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.ai.service.AiConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.controller.AiConfigController;

/** 模型配置含 apiKey，属敏感凭据：无权限不得触达服务；读取必须脱敏；回传脱敏值不得覆盖真实密钥。 */
class AiConfigControllerTest {
    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private void login(long userId) {
        var user = new LoginUser();
        user.setUserId(userId);
        user.setPermissions(Set.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void listWithoutPermissionIsDeniedBeforeTouchingService() {
        var service = mock(AiConfigService.class);
        var permissions = mock(AiAgentPermissionService.class);
        login(7L);
        when(permissions.allows(7L, "ai:config:list")).thenReturn(false);
        var controller = new AiConfigController(service, permissions);
        assertThrows(AccessDeniedException.class, controller::list);
        assertThrows(AccessDeniedException.class, () -> controller.getById(1L));
        assertThrows(AccessDeniedException.class, () -> controller.add(new AiConfig()));
        assertThrows(AccessDeniedException.class, () -> controller.edit(new AiConfig()));
        assertThrows(AccessDeniedException.class, () -> controller.remove(1L));
        assertThrows(AccessDeniedException.class, () -> controller.setDefault(1L));
        verifyNoInteractions(service);
    }

    @Test
    void listMasksApiKeyButKeepsOtherFields() {
        var service = mock(AiConfigService.class);
        var permissions = mock(AiAgentPermissionService.class);
        login(7L);
        when(permissions.allows(7L, "ai:config:list")).thenReturn(true);
        var config = new AiConfig();
        config.setId(1L);
        config.setName("deepseek");
        config.setApiKey("sk-1234567890abcdef");
        when(service.list()).thenReturn(List.of(config));
        var controller = new AiConfigController(service, permissions);
        AjaxResult result = controller.list();
        @SuppressWarnings("unchecked")
        List<AiConfig> data = (List<AiConfig>) result.get(AjaxResult.DATA_TAG);
        assertEquals("sk-1****cdef", data.get(0).getApiKey());
        assertEquals("deepseek", data.get(0).getName());
    }

    @Test
    void editWithMaskedKeyPreservesStoredSecret() {
        var service = mock(AiConfigService.class);
        var permissions = mock(AiAgentPermissionService.class);
        login(7L);
        when(permissions.allows(7L, "ai:config:list")).thenReturn(true);
        var stored = new AiConfig();
        stored.setId(1L);
        stored.setApiKey("sk-real-secret-key");
        when(service.getById(1L)).thenReturn(stored);
        var incoming = new AiConfig();
        incoming.setId(1L);
        incoming.setApiKey("sk-r****-key");
        var controller = new AiConfigController(service, permissions);
        controller.edit(incoming);
        assertEquals("sk-real-secret-key", incoming.getApiKey());
        verify(service).updateById(incoming);
    }

    @Test
    void shortOrBlankKeysAreFullyMasked() {
        assertEquals("****", AiConfigController.maskApiKey("abc"));
        assertEquals("****", AiConfigController.maskApiKey("12345678"));
        assertEquals("****", AiConfigController.maskApiKey(" "));
        assertNull(AiConfigController.maskApiKey(null));
        assertEquals("abcd****wxyz", AiConfigController.maskApiKey("abcdmiddlewxyz"));
    }
}
