package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.json.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PythonAgentClient {
    /** 普通只读查询的读超时。 */
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 30000;
    /**
     * 模型型分析接口的读超时：低于前端 65s，保证服务端先返回可读错误而不是让客户端先超时；
     * 同时覆盖 Python 侧 20s 模型超时与余量。
     */
    private static final int MODEL_READ_TIMEOUT_MILLIS = 60000;

    private final URI endpoint;
    private final String secret;
    private final Path configFile;
    private final Transport transport;
    private final Transport modelTransport;
    record Response(int status, String body) {}
    interface Transport { Response send(URI uri, String secret, byte[] body) throws IOException; }
    @Autowired
    public PythonAgentClient(@Value("${ERP_AGENT_URL:http://127.0.0.1:8000}") String base,
                             @Value("${ERP_AGENT_SECRET:}") String secret,
                             @Value("${ERP_AGENT_CONFIG:}") String config) {
        this(base, secret, config == null || config.isBlank() ? defaultConfig() : Path.of(config),
                (uri, sharedSecret, body) -> post(uri, sharedSecret, body, DEFAULT_READ_TIMEOUT_MILLIS),
                (uri, sharedSecret, body) -> post(uri, sharedSecret, body, MODEL_READ_TIMEOUT_MILLIS));
    }
    PythonAgentClient(String base, String secret, Transport transport) {
        this(base, secret, defaultConfig(), transport, transport);
    }
    private static Path defaultConfig() {
        for (Path path : new Path[]{Path.of("ai-service/config.local.json"), Path.of("../ai-service/config.local.json"), Path.of("../../ai-service/config.local.json")}) {
            if (Files.isRegularFile(path)) return path;
        }
        return Path.of("ai-service/config.local.json");
    }
    PythonAgentClient(String base, String secret, Path config, Transport transport) {
        this(base, secret, config, transport, transport);
    }
    PythonAgentClient(String base, String secret, Path config, Transport transport, Transport modelTransport) {
        this.endpoint = URI.create(base.replaceAll("/+$", "") + "/internal/inventory-analysis");
        this.secret = secret; this.configFile = config; this.transport = transport; this.modelTransport = modelTransport;
    }
    public JsonNode analyze(Object facts) { return send(facts, endpoint, transport); }
    public JsonNode productDraft(Object facts) { return send(facts, endpoint.resolve("/internal/product-draft"), transport); }
    public JsonNode managementAnalysis(Object facts) { return send(facts, endpoint.resolve("/internal/management-analysis"), transport); }
    public JsonNode managementPlan(Object question) { return send(question, endpoint.resolve("/internal/management-plan"), transport); }
    public JsonNode marketingAnalysis(Object facts) { return send(facts, endpoint.resolve("/internal/marketing-analysis"), modelTransport); }
    public JsonNode riskAnalysis(Object facts) { return send(facts, endpoint.resolve("/internal/risk-analysis"), modelTransport); }
    public JsonNode syncInventoryReview(Object task) { return send(task, endpoint.resolve("/internal/inventory-review/sync"), transport); }
    private JsonNode send(Object facts, URI target, Transport transport) {
        String sharedSecret = resolveSecret();
        if (sharedSecret.length() < 32) throw new IllegalStateException("AI 服务密钥未配置或长度不足");
        if (!"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost()) || endpoint.getUserInfo() != null)
            throw new IllegalStateException("AI 服务地址必须是本机 127.0.0.1 HTTP 地址");
        try {
            Response response = transport.send(target, sharedSecret, JsonUtils.write(facts).getBytes(StandardCharsets.UTF_8));
            if (response.status() != 200 || response.body().length() > 1000000) throw new IllegalStateException("AI 服务调用失败");
            return JsonUtils.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("无法连接本机 AI 服务", e);
        }
    }
    private String resolveSecret() {
        if (secret != null && !secret.isBlank()) return secret;
        try {
            if (!Files.isRegularFile(configFile)) return "";
            var config = JsonUtils.readTree(Files.readString(configFile, StandardCharsets.UTF_8));
            String configured = config == null ? null : JsonUtils.text(config,"agent_secret");
            return configured == null ? "" : configured;
        } catch (Exception e) { throw new IllegalStateException("无法读取 AI 本机配置文件"); }
    }
    private static Response post(URI uri, String secret, byte[] body, int readTimeoutMillis) throws IOException {
        var connection = (HttpURLConnection) uri.toURL().openConnection(java.net.Proxy.NO_PROXY);
        connection.setConnectTimeout(3000); connection.setReadTimeout(readTimeoutMillis); connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json"); connection.setRequestProperty("X-Agent-Secret", secret);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (var out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            if (status != 200) return new Response(status, "");
            try (var in = connection.getInputStream()) {
                byte[] bytes = in.readNBytes(1000001);
                if (bytes.length > 1000000) throw new IOException("AI 服务响应过大");
                return new Response(status, new String(bytes, StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
}
