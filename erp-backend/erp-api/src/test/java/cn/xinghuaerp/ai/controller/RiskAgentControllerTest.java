package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.ai.service.AiRiskReadService;
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
import cn.xinghuaerp.ai.controller.RiskAgentController;

/** 售后风险智能体接口的权限、主体范围与事实组装测试。 */
class RiskAgentControllerTest {
    final AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
    final BusinessAccessService access = mock(BusinessAccessService.class);
    final AiRiskReadService risk = mock(AiRiskReadService.class);
    final PythonAgentClient python = mock(PythonAgentClient.class);
    final RiskAgentController controller = new RiskAgentController(permissions, access, risk, python);

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
        verifyNoInteractions(access, risk, python);
    }

    @Test
    void unsupportedSubjectDoesNotReadServiceOrSendFacts() {
        when(permissions.allows(7L, "ai:risk:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(new Scope("WAREHOUSE", 1L, null, null));
        assertThrows(AccessDeniedException.class,
                () -> controller.analyze(new RiskAgentController.Request("有哪些风险", false)));
        verifyNoInteractions(risk, python);
        when(access.scope(7L)).thenReturn(new Scope("SUPPLIER", 1L, null, null));
        assertThrows(AccessDeniedException.class, () -> controller.analyze(null));
        verifyNoInteractions(risk, python);
    }

    @Test
    void usesServerScopeAndPassesFindingsToModel() {
        when(permissions.allows(7L, "ai:risk:query")).thenReturn(true);
        var scope = new Scope("MERCHANT", 10L, 10L, null);
        when(access.scope(7L)).thenReturn(scope);
        var findings = List.of(
                new AiRiskReadService.Finding("STALE_PENDING", "HIGH", "待处理超3天", 4, "核对原单与售后进度"),
                new AiRiskReadService.Finding("AMOUNT_INVALID", "MEDIUM", "退款金额缺失或非正", 2, "核对申请金额"));
        when(risk.overview(scope)).thenReturn(
                new AiRiskReadService.Overview("绑定商户", 21, 13, findings, "仅统计当前授权范围内内部售后记录"));

        controller.analyze(new RiskAgentController.Request("  退款风险如何  ", true));

        verify(access).scope(7L);
        verify(risk).overview(scope);
        verify(python).riskAnalysis(argThat(value -> value instanceof RiskAgentController.Facts facts
                && "绑定商户".equals(facts.scope())
                && facts.total() == 21 && facts.open() == 13
                && facts.findings() == findings
                && "退款风险如何".equals(facts.question())
                && facts.useModel()));
    }

    @Test
    void questionOver500CharactersDoesNotReadOrSendFacts() {
        when(permissions.allows(7L, "ai:risk:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        String longQuestion = "退".repeat(501);
        assertThrows(IllegalArgumentException.class,
                () -> controller.analyze(new RiskAgentController.Request(longQuestion, false)));
        verifyNoInteractions(risk, python);
    }

    @Test
    void missingRequestBodyFallsBackToEmptyQuestionAndModelOff() {
        when(permissions.allows(7L, "ai:risk:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        when(risk.overview(ArgumentMatchers.any()))
                .thenReturn(new AiRiskReadService.Overview("总部", 3, 1, List.of(), "限制"));

        controller.analyze(null);

        verify(python).riskAnalysis(argThat(value -> value instanceof RiskAgentController.Facts facts
                && facts.total() == 3 && facts.open() == 1
                && facts.question().isEmpty() && !facts.useModel() && facts.findings().isEmpty()));
    }

    @Test
    void modelFailureIsTranslatedToErrorResult() {
        var result = controller.invalid(new IllegalStateException("AI 服务调用失败"));

        assertEquals("AI 服务调用失败", result.get("msg"));
    }

    @Test
    void factsPayloadMatchesPythonContract() {
        when(permissions.allows(7L, "ai:risk:query")).thenReturn(true);
        var scope = new Scope("MERCHANT", 10L, 10L, null);
        when(access.scope(7L)).thenReturn(scope);
        when(risk.overview(scope)).thenReturn(new AiRiskReadService.Overview("绑定商户", 9, 4,
                List.of(new AiRiskReadService.Finding("STALE_PENDING", "HIGH", "待处理超3天", 3, "核对原单与售后进度")), "限制"));

        controller.analyze(new RiskAgentController.Request("模型问题", true));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(python).riskAnalysis(captor.capture());
        var json = JsonUtils.readTree(JsonUtils.write(captor.getValue()));
        assertEquals(7, json.size(), "字段集合必须与 Python 侧 extra=forbid 严格一致");
        assertEquals("绑定商户", json.get("scope").asString());
        assertEquals(9, json.get("total").asInt());
        assertEquals(4, json.get("open").asInt());
        assertEquals("模型问题", json.get("question").asString());
        assertTrue(json.get("useModel").asBoolean());
        assertEquals(AiRiskReadService.LIMITATION, json.get("limitation").asString(),
                "资料口径必须由 Java 常量单点提供，Python 与前端只透传");
        var finding = json.get("findings").get(0);
        assertEquals(5, finding.size());
        assertEquals("STALE_PENDING", finding.get("code").asString());
        assertEquals("HIGH", finding.get("level").asString());
        assertEquals("待处理超3天", finding.get("title").asString());
        assertEquals(3, finding.get("count").asInt());
        assertEquals("核对原单与售后进度", finding.get("advice").asString());
    }

    @Test
    void emptyFindingsBecomeEmptyArrayAndQuestionBlankedWhenModelOff() {
        when(permissions.allows(7L, "ai:risk:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        when(risk.overview(ArgumentMatchers.any()))
                .thenReturn(new AiRiskReadService.Overview("总部", 0, 0, List.of(), "限制"));

        controller.analyze(new RiskAgentController.Request("  不想用模型  ", false));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(python).riskAnalysis(captor.capture());
        var json = JsonUtils.readTree(JsonUtils.write(captor.getValue()));
        assertTrue(json.get("findings").isArray(), "空发现项必须序列化为 []，不能是 null");
        assertEquals(0, json.get("findings").size());
        assertEquals(7, json.size());
        assertEquals("", json.get("question").asString(), "未启用模型时不把问题文本转给 AI 服务");
        assertFalse(json.get("useModel").asBoolean());
        assertEquals(AiRiskReadService.LIMITATION, json.get("limitation").asString(),
                "limitation 是资料口径不是提示词，未启用模型时同样必须传递");
    }
}
