package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.ai.entity.AiConfig;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.nio.file.Files;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;
import cn.xinghuaerp.json.JsonUtils;
import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.ai.service.PythonAgentClient;

class PythonAgentClientTest {
    @Test void managementUsesItsOwnEndpointAndSerializesOptIn() {
        record Facts(int days, boolean useModel, String question) {}
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32), (uri, headers, body) -> {
            assertEquals("/internal/management-analysis", uri.getPath());
            var facts = JsonUtils.readTree(new String(body, StandardCharsets.UTF_8));
            assertEquals(7, facts.get("days").asInt());
            assertTrue(facts.get("useModel").asBoolean());
            assertEquals("分析限制", facts.get("question").asString());
            return new PythonAgentClient.Response(200, "{\"modelStatus\":\"disabled\"}");
        });
        assertEquals("disabled", client.managementAnalysis(new Facts(7, true, "分析限制")).get("modelStatus").asString());
    }
    @Test void sendsSnapshotToLoopbackTransport() {
        var uri = new AtomicReference<String>(); var header = new AtomicReference<String>();
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32), (u, headers, data) -> {
            uri.set(u.toString()); header.set(headers.secret()); return new PythonAgentClient.Response(200, "{\"summary\":\"ok\"}");
        });
        assertEquals("ok",client.analyze(Map.of("skuId",5)).get("summary").asString());
        assertEquals("s".repeat(32),header.get()); assertEquals("http://127.0.0.1:8000/internal/inventory-analysis", uri.get());
    }
    @Test void readsSharedSecretFromLocalConfiguration(@TempDir Path directory) throws Exception {
        var file = directory.resolve("config.local.json");
        Files.writeString(file, "{\"agent_secret\":\"" + "q".repeat(32) + "\"}");
        var client = new PythonAgentClient("http://127.0.0.1:8000", "", file, (uri, headers, body) -> {
            assertEquals("q".repeat(32), headers.secret()); return new PythonAgentClient.Response(200, "{}");
        });
        assertNotNull(client.analyze(Map.of()));
    }
    @Test void rejectsWeakSecretAndNonLoopbackBeforeTransport() {
        assertThrows(IllegalStateException.class, () -> new PythonAgentClient("http://127.0.0.1:1", "weak", (u,h,b) -> { throw new AssertionError(); }).analyze(Map.of()));
        assertThrows(IllegalStateException.class, () -> new PythonAgentClient("http://example.com", "s".repeat(32), (u,h,b) -> { throw new AssertionError(); }).analyze(Map.of()));
    }
    @Test void marketingAndRiskUseOwnEndpointsOnModelTransport() {
        var seen = new java.util.ArrayList<String>();
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32), Path.of("missing-config.json"),
                (uri, headers, body) -> { seen.add(uri.getPath()); return new PythonAgentClient.Response(200, "{}"); },
                (uri, headers, body) -> { seen.add("model:" + uri.getPath()); return new PythonAgentClient.Response(200, "{}"); });
        client.marketingAnalysis(Map.of("scope", "总部"));
        client.riskAnalysis(Map.of("scope", "总部"));
        client.managementAnalysis(Map.of());
        assertEquals(java.util.List.of("model:/internal/marketing-analysis",
                "model:/internal/risk-analysis", "/internal/management-analysis"), seen);
    }

    /** 页面配置的模型参数必须随请求透传，Python 侧据此覆盖本地配置文件。 */
    @Test void forwardsPageConfiguredModelToPython() {
        var config = new AiConfig();
        config.setApiKey("sk-from-page");
        config.setApiEndpoint("https://api.deepseek.com/v1");
        config.setModelName("deepseek-chat");
        var seen = new AtomicReference<PythonAgentClient.AgentHeaders>();
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32), Path.of("missing-config.json"),
                () -> config,
                (uri, headers, body) -> { seen.set(headers); return new PythonAgentClient.Response(200, "{}"); },
                (uri, headers, body) -> { seen.set(headers); return new PythonAgentClient.Response(200, "{}"); });
        client.marketingAnalysis(Map.of("scope", "总部"));
        assertEquals("sk-from-page", seen.get().modelApiKey());
        assertEquals("https://api.deepseek.com/v1", seen.get().modelBaseUrl());
        assertEquals("deepseek-chat", seen.get().modelName());
        assertEquals("s".repeat(32), seen.get().secret());
    }

    /** 未配置默认模型时不传模型头，Python 侧保持环境变量/本地配置的原有行为。 */
    @Test void omitsModelHeadersWhenNothingConfigured() {
        var seen = new AtomicReference<PythonAgentClient.AgentHeaders>();
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32), Path.of("missing-config.json"),
                () -> null,
                (uri, headers, body) -> { seen.set(headers); return new PythonAgentClient.Response(200, "{}"); },
                (uri, headers, body) -> { seen.set(headers); return new PythonAgentClient.Response(200, "{}"); });
        assertNotNull(client.analyze(Map.of()));
        assertEquals("", seen.get().modelApiKey());
    }

    /** 读模型配置失败不能把 AI 调用一起拖垮。 */
    @Test void modelLookupFailureDoesNotBreakAgentCall() {
        var seen = new AtomicReference<PythonAgentClient.AgentHeaders>();
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32), Path.of("missing-config.json"),
                () -> { throw new IllegalStateException("数据库不可用"); },
                (uri, headers, body) -> { seen.set(headers); return new PythonAgentClient.Response(200, "{}"); },
                (uri, headers, body) -> { seen.set(headers); return new PythonAgentClient.Response(200, "{}"); });
        assertNotNull(client.analyze(Map.of()));
        assertEquals("", seen.get().modelApiKey());
    }

    /** 含换行的密钥会被 HttpURLConnection 判为非法头，必须在发出前丢弃。 */
    @Test void dropsModelHeaderContainingNewline() {
        var config = new AiConfig();
        config.setApiKey("sk-bad\r\nX-Injected: 1");
        config.setModelName("deepseek-chat");
        var seen = new AtomicReference<PythonAgentClient.AgentHeaders>();
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32), Path.of("missing-config.json"),
                () -> config,
                (uri, headers, body) -> { seen.set(headers); return new PythonAgentClient.Response(200, "{}"); },
                (uri, headers, body) -> { seen.set(headers); return new PythonAgentClient.Response(200, "{}"); });
        assertNotNull(client.analyze(Map.of()));
        assertEquals("", seen.get().modelApiKey());
        assertEquals("deepseek-chat", seen.get().modelName());
    }

    /** 流式响应按行解析：事件类型原样透传，非 JSON 行降级为文本，不能整段丢弃。 */
    @Test void dispatchesNdjsonEventsAndKeepsPlainText() {
        var seen = new java.util.ArrayList<String>();
        PythonAgentClient.StreamListener listener = (type, content) -> seen.add(type + ":" + content);
        PythonAgentClient.dispatch("{\"type\":\"message\",\"content\":\"你好\"}", listener);
        PythonAgentClient.dispatch("{\"type\":\"done\"}", listener);
        PythonAgentClient.dispatch("纯文本一行", listener);
        assertEquals(java.util.List.of("message:你好", "done:", "message:纯文本一行"), seen);
    }

    /** 错误体里的 detail 要透出，否则页面只能看到「调用失败」而不知道原因。 */
    @Test void surfacesPythonErrorDetail() {
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32),
                (uri, headers, body) -> new PythonAgentClient.Response(409, "{\"detail\":\"未配置已启用的模型\"}"));
        var error = assertThrows(IllegalStateException.class, () -> client.brief(Map.of("prompt", "x"), ""));
        assertEquals("未配置已启用的模型", error.getMessage());
    }
}
