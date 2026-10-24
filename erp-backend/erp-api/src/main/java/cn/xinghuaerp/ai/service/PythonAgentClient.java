package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.ai.entity.AiConfig;
import cn.xinghuaerp.json.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

/**
 * AI 服务客户端。
 *
 * <p>模型调用全部由 Python 服务承担，本类只做传输：把上下文送过去、把结果取回来。
 * 页面配置的模型凭据与工具令牌都通过请求头透传，Java 不保存会话态。</p>
 */
@Service
public class PythonAgentClient {
    /** 普通只读查询的读超时。 */
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 30000;
    /**
     * 模型型分析接口的读超时：低于前端 65s，保证服务端先返回可读错误而不是让客户端先超时；
     * 同时覆盖 Python 侧 20s 模型超时与余量。
     */
    private static final int MODEL_READ_TIMEOUT_MILLIS = 60000;
    /** 流式对话的读超时：按段返回，前端总时长上限 180s，这里留出余量。 */
    private static final int STREAM_READ_TIMEOUT_MILLIS = 180000;

    private final URI endpoint;
    private final String secret;
    private final Path configFile;
    private final Supplier<AiConfig> modelConfig;
    private final Transport transport;
    private final Transport modelTransport;

    public record Response(int status, String body) {}

    /**
     * 单次调用的请求头集合。模型参数取自「AI 智能 &gt; 模型配置」页面保存的记录，
     * 工具令牌用于 Python 回调 Java 查询数据时证明发起人身份。
     */
    record AgentHeaders(String secret, String modelApiKey, String modelBaseUrl, String modelName, String toolToken) {}

    public interface Transport { Response send(URI uri, AgentHeaders headers, byte[] body) throws IOException; }

    /** 流式事件回调：type 取 message / done / error，与前端 SSE 事件同名，中间不做语义转换。 */
    public interface StreamListener { void onEvent(String type, String content); }

    @Autowired
    public PythonAgentClient(@Value("${ERP_AGENT_URL:http://127.0.0.1:8000}") String base,
                             @Value("${ERP_AGENT_SECRET:}") String secret,
                             @Value("${ERP_AGENT_CONFIG:}") String config,
                             AiConfigService aiConfigService) {
        this(base, secret, config == null || config.isBlank() ? defaultConfig() : Path.of(config),
                aiConfigService::getDefaultConfig,
                (uri, headers, body) -> post(uri, headers, body, DEFAULT_READ_TIMEOUT_MILLIS),
                (uri, headers, body) -> post(uri, headers, body, MODEL_READ_TIMEOUT_MILLIS));
    }
    PythonAgentClient(String base, String secret, Transport transport) {
        this(base, secret, defaultConfig(), null, transport, transport);
    }
    private static Path defaultConfig() {
        for (Path path : new Path[]{Path.of("ai-service/config.local.json"), Path.of("../ai-service/config.local.json"), Path.of("../../ai-service/config.local.json")}) {
            if (Files.isRegularFile(path)) return path;
        }
        return Path.of("ai-service/config.local.json");
    }
    PythonAgentClient(String base, String secret, Path config, Transport transport) {
        this(base, secret, config, null, transport, transport);
    }
    PythonAgentClient(String base, String secret, Path config, Transport transport, Transport modelTransport) {
        this(base, secret, config, null, transport, modelTransport);
    }
    PythonAgentClient(String base, String secret, Path config, Supplier<AiConfig> modelConfig, Transport transport, Transport modelTransport) {
        this.endpoint = URI.create(base.replaceAll("/+$", "") + "/internal/inventory-analysis");
        this.secret = secret; this.configFile = config; this.modelConfig = modelConfig;
        this.transport = transport; this.modelTransport = modelTransport;
    }

    public JsonNode analyze(Object facts) { return send(facts, endpoint, transport, ""); }
    public JsonNode productDraft(Object facts) { return send(facts, endpoint.resolve("/internal/product-draft"), transport, ""); }
    public JsonNode managementAnalysis(Object facts) { return send(facts, endpoint.resolve("/internal/management-analysis"), transport, ""); }
    public JsonNode managementPlan(Object question) { return send(question, endpoint.resolve("/internal/management-plan"), transport, ""); }
    public JsonNode marketingAnalysis(Object facts) { return send(facts, endpoint.resolve("/internal/marketing-analysis"), modelTransport, ""); }
    public JsonNode riskAnalysis(Object facts) { return send(facts, endpoint.resolve("/internal/risk-analysis"), modelTransport, ""); }
    public JsonNode syncInventoryReview(Object task) { return send(task, endpoint.resolve("/internal/inventory-review/sync"), transport, ""); }

    /** 工作台简报：模型侧一次性生成，Java 只解析结果。 */
    public JsonNode brief(Object body, String toolToken) {
        return send(body, endpoint.resolve("/internal/brief"), modelTransport, toolToken);
    }

    /** 连通性自检：用页面保存的配置发一次最小请求，结果由 Python 侧判定。 */
    public JsonNode modelPing(Object body, String toolToken) {
        return send(body, endpoint.resolve("/internal/model-ping"), transport, toolToken);
    }

    /**
     * 用一条尚未设为默认的配置做连通性测试：请求头直接取该条记录，
     * 不走「默认模型」，否则测试的就不是页面上这一条。
     */
    public JsonNode modelPing(AiConfig config, String toolToken) {
        if (config == null) throw new IllegalArgumentException("模型配置不存在");
        AgentHeaders headers = new AgentHeaders(resolveSecret(), header(config.getApiKey()),
                header(config.getApiEndpoint()), header(config.getModelName()), toolToken == null ? "" : toolToken);
        requireUsable(headers);
        try {
            Response response = transport.send(endpoint.resolve("/internal/model-ping"), headers,
                    JsonUtils.write(Map.of()).getBytes(StandardCharsets.UTF_8));
            if (response.status() != 200) throw new IllegalStateException(failure(response.status(), response.body()));
            return JsonUtils.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("无法连接本机 AI 服务", e);
        }
    }

    /** 共享密钥，供工具网关做同一套校验。 */
    public String agentSecret() { return resolveSecret(); }

    /**
     * 流式调用：把 Python 侧逐行返回的事件原样交给回调。
     * 中途断流抛异常，由调用方转成前端可见的兜底提示。
     */
    public void stream(String path, Object body, String toolToken, StreamListener listener) {
        AgentHeaders headers = callHeaders(toolToken);
        requireUsable(headers);
        var connection = open(endpoint.resolve(path), headers, JsonUtils.write(body).getBytes(StandardCharsets.UTF_8), STREAM_READ_TIMEOUT_MILLIS);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IllegalStateException(failure(status, read(connection.getErrorStream(), 2000)));
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) dispatch(line, listener);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("AI 服务响应中断", e);
        } finally {
            connection.disconnect();
        }
    }

    /** 单行 NDJSON 转事件；解析不了的行走 message 兜底，避免整段输出因一行格式问题丢失。 */
    static void dispatch(String line, StreamListener listener) {
        String type = "message";
        String content = line;
        try {
            JsonNode node = JsonUtils.readTree(line);
            String parsedType = JsonUtils.text(node, "type");
            if (parsedType != null && !parsedType.isBlank()) type = parsedType;
            String parsedContent = JsonUtils.text(node, "content");
            content = parsedContent == null ? "" : parsedContent;
        } catch (RuntimeException ignored) {
            // 非 JSON 行按纯文本透出，保留内容比丢弃更接近可用。
        }
        listener.onEvent(type, content);
    }

    private JsonNode send(Object facts, URI target, Transport transport, String toolToken) {
        AgentHeaders headers = callHeaders(toolToken);
        requireUsable(headers);
        try {
            Response response = transport.send(target, headers, JsonUtils.write(facts).getBytes(StandardCharsets.UTF_8));
            if (response.status() != 200 || response.body().length() > 1000000) {
                throw new IllegalStateException(failure(response.status(), response.body()));
            }
            return JsonUtils.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("无法连接本机 AI 服务", e);
        }
    }

    private void requireUsable(AgentHeaders headers) {
        if (headers.secret().length() < 32) throw new IllegalStateException("AI 服务密钥未配置或长度不足");
        if (!"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost()) || endpoint.getUserInfo() != null)
            throw new IllegalStateException("AI 服务地址必须是本机 127.0.0.1 HTTP 地址");
    }

    /** 把 Python 侧的结构化错误（detail）转成一句人话，测试连接与兜底提示都用它。 */
    private static String failure(int status, String body) {
        String detail = null;
        try {
            JsonNode node = JsonUtils.readTree(body == null ? "" : body);
            detail = JsonUtils.text(node, "detail");
        } catch (RuntimeException ignored) {
            // 非 JSON 错误体走下面的通用文案。
        }
        if (detail != null && !detail.isBlank()) return detail;
        return status == 200 ? "AI 服务返回内容过大" : "AI 服务调用失败（HTTP " + status + "）";
    }

    private AgentHeaders callHeaders(String toolToken) {
        String sharedSecret = resolveSecret();
        AiConfig model = resolveModelConfig();
        String token = toolToken == null ? "" : toolToken;
        if (model == null) return new AgentHeaders(sharedSecret, "", "", "", token);
        return new AgentHeaders(sharedSecret, header(model.getApiKey()), header(model.getApiEndpoint()), header(model.getModelName()), token);
    }
    /** 读取页面配置的默认模型；读库异常不阻断调用，Python 侧会回退到环境变量与本地配置文件。 */
    private AiConfig resolveModelConfig() {
        if (modelConfig == null) return null;
        try {
            return modelConfig.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
    /** 头部值不允许换行，否则会被 HttpURLConnection 判为非法头；含换行直接丢弃该字段。 */
    private static String header(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0 ? "" : trimmed;
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

    private static HttpURLConnection open(URI uri, AgentHeaders headers, byte[] body, int readTimeoutMillis) {
        try {
            var connection = (HttpURLConnection) uri.toURL().openConnection(java.net.Proxy.NO_PROXY);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Agent-Secret", headers.secret());
            if (!headers.modelApiKey().isEmpty()) connection.setRequestProperty("X-Model-Api-Key", headers.modelApiKey());
            if (!headers.modelBaseUrl().isEmpty()) connection.setRequestProperty("X-Model-Base-Url", headers.modelBaseUrl());
            if (!headers.modelName().isEmpty()) connection.setRequestProperty("X-Model-Name", headers.modelName());
            if (!headers.toolToken().isEmpty()) connection.setRequestProperty("X-Tool-Token", headers.toolToken());
            connection.setFixedLengthStreamingMode(body.length);
            try (var out = connection.getOutputStream()) { out.write(body); }
            return connection;
        } catch (IOException e) {
            throw new IllegalStateException("无法连接本机 AI 服务", e);
        }
    }

    private static Response post(URI uri, AgentHeaders headers, byte[] body, int readTimeoutMillis) throws IOException {
        var connection = open(uri, headers, body, readTimeoutMillis);
        try {
            int status = connection.getResponseCode();
            String payload = read(status == 200 ? connection.getInputStream() : connection.getErrorStream(), 1000001);
            if (payload == null) payload = "";
            if (payload.length() > 1000000) throw new IOException("AI 服务响应过大");
            return new Response(status, payload);
        } finally { connection.disconnect(); }
    }

    /** 限量读取：不信任对端长度，超过上限立即截断，避免异常响应占满内存。 */
    private static String read(InputStream stream, int limit) throws IOException {
        if (stream == null) return "";
        try (stream) {
            byte[] bytes = stream.readNBytes(limit);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
