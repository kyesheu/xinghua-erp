package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.ai.entity.AiConfig;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.ai.service.AiConfigService;
import cn.xinghuaerp.ai.service.impl.AiOrchestrationService;
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
        var orchestration = mock(AiOrchestrationService.class);
        login(7L);
        when(permissions.allows(7L, "ai:config:list")).thenReturn(false);
        var controller = new AiConfigController(service, permissions, orchestration);
        assertThrows(AccessDeniedException.class, controller::list);
        assertThrows(AccessDeniedException.class, () -> controller.getById(1L));
        assertThrows(AccessDeniedException.class, () -> controller.add(new AiConfig()));
        assertThrows(AccessDeniedException.class, () -> controller.edit(new AiConfig()));
        assertThrows(AccessDeniedException.class, () -> controller.remove(1L));
        assertThrows(AccessDeniedException.class, () -> controller.setDefault(1L));
        assertThrows(AccessDeniedException.class, () -> controller.test(1L));
        verifyNoInteractions(service);
        verifyNoInteractions(orchestration);
    }

    @Test
    void listMasksApiKeyButKeepsOtherFields() {
        var service = mock(AiConfigService.class);
        var permissions = mock(AiAgentPermissionService.class);
        var orchestration = mock(AiOrchestrationService.class);
        login(7L);
        when(permissions.allows(7L, "ai:config:list")).thenReturn(true);
        var config = new AiConfig();
        config.setId(1L);
        config.setName("deepseek");
        config.setApiKey("sk-1234567890abcdef");
        when(service.list()).thenReturn(List.of(config));
        var controller = new AiConfigController(service, permissions, orchestration);
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
        var orchestration = mock(AiOrchestrationService.class);
        login(7L);
        when(permissions.allows(7L, "ai:config:list")).thenReturn(true);
        var stored = new AiConfig();
        stored.setId(1L);
        stored.setApiKey("sk-real-secret-key");
        when(service.getById(1L)).thenReturn(stored);
        var incoming = new AiConfig();
        incoming.setId(1L);
        incoming.setApiKey("sk-r****-key");
        var controller = new AiConfigController(service, permissions, orchestration);
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

    /** 测试连接用库中原文（非掩码值）验证，成功回摘要、失败回可读原因。 */
    @Test
    void testConnectionUsesStoredSecretAndReportsReadableFailure() {
        var service = mock(AiConfigService.class);
        var permissions = mock(AiAgentPermissionService.class);
        var orchestration = mock(AiOrchestrationService.class);
        login(7L);
        when(permissions.allows(7L, "ai:config:list")).thenReturn(true);
        var config = new AiConfig();
        config.setId(1L);
        config.setApiKey("sk-real-secret-key");
        config.setApiEndpoint("https://api.deepseek.com/v1");
        config.setModelName("deepseek-chat");
        when(service.getById(1L)).thenReturn(config);
        when(orchestration.testConnection(config)).thenReturn("pong");
        var controller = new AiConfigController(service, permissions, orchestration);

        AjaxResult ok = controller.test(1L);
        assertTrue(ok.isSuccess());
        verify(orchestration).testConnection(config);

        when(orchestration.testConnection(config)).thenThrow(new IllegalStateException("401 Unauthorized"));
        AjaxResult failed = controller.test(1L);
        // 项目里 error(msg) 走 ResultVoEnum.API_FAIL，不恒等于 HttpStatus.ERROR，故按「非成功」判定
        assertFalse(failed.isSuccess());
        String message = String.valueOf(failed.get(AjaxResult.MSG_TAG));
        assertTrue(message.contains("401"), "失败原因应透出，便于定位是密钥还是地址问题");
        assertFalse(message.contains("sk-real-secret-key"), "失败提示绝不能回显密钥");
    }

    @Test
    void testConnectionOnMissingConfigDoesNotTouchModel() {
        var service = mock(AiConfigService.class);
        var permissions = mock(AiAgentPermissionService.class);
        var orchestration = mock(AiOrchestrationService.class);
        login(7L);
        when(permissions.allows(7L, "ai:config:list")).thenReturn(true);
        when(service.getById(99L)).thenReturn(null);
        var controller = new AiConfigController(service, permissions, orchestration);

        assertFalse(controller.test(99L).isSuccess());
        verifyNoInteractions(orchestration);
    }
}
