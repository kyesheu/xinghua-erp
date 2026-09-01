package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.ai.service.AiMarketingReadService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import cn.xinghuaerp.ai.controller.MarketingAgentController;

/** 营销智能体接口的权限、主体范围与事实组装测试。 */
class MarketingAgentControllerTest {
    final AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
    final BusinessAccessService access = mock(BusinessAccessService.class);
    final AiMarketingReadService marketing = mock(AiMarketingReadService.class);
    final PythonAgentClient python = mock(PythonAgentClient.class);
    final MarketingAgentController controller = new MarketingAgentController(permissions, access, marketing, python);

    @BeforeEach
    void login() {
        var user = new LoginUser();
        user.setUserId(7L);
        user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noPermissionDoesNotReadOrSendFacts() {
        assertThrows(AccessDeniedException.class, () -> controller.analyze(null));
        verifyNoInteractions(access, marketing, python);
    }

    @Test
    void unsupportedSubjectDoesNotReadServiceOrSendFacts() {
        when(permissions.allows(7L, "ai:marketing:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(new Scope("WAREHOUSE", 1L, null, null));
        assertThrows(AccessDeniedException.class,
                () -> controller.analyze(new MarketingAgentController.Request("核对折扣", false)));
        verifyNoInteractions(marketing, python);
        when(access.scope(7L)).thenReturn(new Scope("SUPPLIER", 1L, null, null));
        assertThrows(AccessDeniedException.class, () -> controller.analyze(null));
        verifyNoInteractions(marketing, python);
    }

    @Test
    void usesServerScopeAndPassesFindingsToModel() {
        when(permissions.allows(7L, "ai:marketing:query")).thenReturn(true);
        var scope = new Scope("SHOP", 100L, 10L, 100L);
        when(access.scope(7L)).thenReturn(scope);
        var findings = List.of(
                new AiMarketingReadService.Finding("EXPIRED_ENABLED", "HIGH", "已过期但仍启用", 2, "核对结束时间"),
                new AiMarketingReadService.Finding("EXPIRING_SOON", "MEDIUM", "7天内到期", 1, "确认是否续期"));
        when(marketing.overview(scope)).thenReturn(
                new AiMarketingReadService.Overview("绑定店铺", 12, 9, 3, findings, "仅统计当前授权范围内本系统手动订单折扣规则"));

        controller.analyze(new MarketingAgentController.Request("  这批折扣能上线吗  ", true));

        verify(access).scope(7L);
        verify(marketing).overview(scope);
        verify(python).marketingAnalysis(argThat(value -> value instanceof MarketingAgentController.Facts facts
                && "绑定店铺".equals(facts.scope())
                && facts.total() == 12 && facts.active() == 9 && facts.pendingReview() == 3
                && facts.findings() == findings
                && "这批折扣能上线吗".equals(facts.question())
                && facts.useModel()));
    }

    @Test
    void questionOver500CharactersDoesNotReadOrSendFacts() {
        when(permissions.allows(7L, "ai:marketing:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        String longQuestion = "折".repeat(501);
        assertThrows(IllegalArgumentException.class,
                () -> controller.analyze(new MarketingAgentController.Request(longQuestion, false)));
        verifyNoInteractions(marketing, python);
    }

    @Test
    void missingRequestBodyFallsBackToEmptyQuestionAndModelOff() {
        when(permissions.allows(7L, "ai:marketing:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        when(marketing.overview(ArgumentMatchers.any()))
                .thenReturn(new AiMarketingReadService.Overview("总部", 0, 0, 0, List.of(), "限制"));

        controller.analyze(null);

        verify(python).marketingAnalysis(argThat(value -> value instanceof MarketingAgentController.Facts facts
                && facts.question().isEmpty() && !facts.useModel() && facts.findings().isEmpty()));
    }

    @Test
    void modelFailureIsTranslatedToErrorResult() {
        var result = controller.invalid(new IllegalStateException("AI 服务调用失败"));

        assertEquals("AI 服务调用失败", result.get("msg"));
    }

    @Test
    void factsPayloadMatchesPythonContract() {
        when(permissions.allows(7L, "ai:marketing:query")).thenReturn(true);
        var scope = new Scope("HEADQUARTERS", null, null, null);
        when(access.scope(7L)).thenReturn(scope);
        when(marketing.overview(scope)).thenReturn(new AiMarketingReadService.Overview("总部", 5, 3, 2,
                List.of(new AiMarketingReadService.Finding("EXPIRED_ENABLED", "HIGH", "已过期但仍启用", 1, "核对结束时间")), "限制"));

        controller.analyze(new MarketingAgentController.Request("模型问题", true));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(python).marketingAnalysis(captor.capture());
        var json = JsonUtils.readTree(JsonUtils.write(captor.getValue()));
        assertEquals(8, json.size(), "字段集合必须与 Python 侧 extra=forbid 严格一致");
        assertEquals("总部", json.get("scope").asString());
        assertEquals(5, json.get("total").asInt());
        assertEquals(3, json.get("active").asInt());
        assertEquals(2, json.get("pendingReview").asInt());
        assertEquals("模型问题", json.get("question").asString());
        assertTrue(json.get("useModel").asBoolean());
        assertEquals(AiMarketingReadService.LIMITATION, json.get("limitation").asString(),
                "资料口径必须由 Java 常量单点提供，Python 与前端只透传");
        var finding = json.get("findings").get(0);
        assertEquals(5, finding.size());
        assertEquals("EXPIRED_ENABLED", finding.get("code").asString());
        assertEquals("HIGH", finding.get("level").asString());
        assertEquals("已过期但仍启用", finding.get("title").asString());
        assertEquals(1, finding.get("count").asInt());
        assertEquals("核对结束时间", finding.get("advice").asString());
    }

    @Test
    void emptyFindingsBecomeEmptyArrayAndQuestionBlankedWhenModelOff() {
        when(permissions.allows(7L, "ai:marketing:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        when(marketing.overview(ArgumentMatchers.any()))
                .thenReturn(new AiMarketingReadService.Overview("总部", 0, 0, 0, List.of(), "限制"));

        controller.analyze(new MarketingAgentController.Request("  不想用模型  ", false));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(python).marketingAnalysis(captor.capture());
        var json = JsonUtils.readTree(JsonUtils.write(captor.getValue()));
        assertTrue(json.get("findings").isArray(), "空发现项必须序列化为 []，不能是 null");
        assertEquals(0, json.get("findings").size());
        assertEquals(8, json.size());
        assertEquals("", json.get("question").asString(), "未启用模型时不把问题文本转给 AI 服务");
        assertFalse(json.get("useModel").asBoolean());
        assertEquals(AiMarketingReadService.LIMITATION, json.get("limitation").asString(),
                "limitation 是资料口径不是提示词，未启用模型时同样必须传递");
    }
}
