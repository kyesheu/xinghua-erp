package cn.xinghuaerp.ai.service;

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
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32), (uri, secret, body) -> {
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
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32), (u, s, data) -> {
            uri.set(u.toString()); header.set(s); return new PythonAgentClient.Response(200, "{\"summary\":\"ok\"}");
        });
        assertEquals("ok",client.analyze(Map.of("skuId",5)).get("summary").asString());
        assertEquals("s".repeat(32),header.get()); assertEquals("http://127.0.0.1:8000/internal/inventory-analysis", uri.get());
    }
    @Test void readsSharedSecretFromLocalConfiguration(@TempDir Path directory) throws Exception {
        var file = directory.resolve("config.local.json");
        Files.writeString(file, "{\"agent_secret\":\"" + "q".repeat(32) + "\"}");
        var client = new PythonAgentClient("http://127.0.0.1:8000", "", file, (uri, secret, body) -> {
            assertEquals("q".repeat(32), secret); return new PythonAgentClient.Response(200, "{}");
        });
        assertNotNull(client.analyze(Map.of()));
    }
    @Test void rejectsWeakSecretAndNonLoopbackBeforeTransport() {
        assertThrows(IllegalStateException.class, () -> new PythonAgentClient("http://127.0.0.1:1", "weak", (u,s,b) -> { throw new AssertionError(); }).analyze(Map.of()));
        assertThrows(IllegalStateException.class, () -> new PythonAgentClient("http://example.com", "s".repeat(32), (u,s,b) -> { throw new AssertionError(); }).analyze(Map.of()));
    }
    @Test void marketingAndRiskUseOwnEndpointsOnModelTransport() {
        var seen = new java.util.ArrayList<String>();
        var client = new PythonAgentClient("http://127.0.0.1:8000", "s".repeat(32), Path.of("missing-config.json"),
                (uri, secret, body) -> { seen.add(uri.getPath()); return new PythonAgentClient.Response(200, "{}"); },
                (uri, secret, body) -> { seen.add("model:" + uri.getPath()); return new PythonAgentClient.Response(200, "{}"); });
        client.marketingAnalysis(Map.of("scope", "总部"));
        client.riskAnalysis(Map.of("scope", "总部"));
        client.managementAnalysis(Map.of());
        assertEquals(java.util.List.of("model:/internal/marketing-analysis",
                "model:/internal/risk-analysis", "/internal/management-analysis"), seen);
    }
}
