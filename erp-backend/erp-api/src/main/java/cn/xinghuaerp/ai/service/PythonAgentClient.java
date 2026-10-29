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

    private static final java.util.concurrent.ScheduledExecutorService STREAM_WATCHDOG = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        var thread=new Thread(r,"ai-stream-watchdog");thread.setDaemon(true);return thread;
    });
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PythonAgentClient.class);
    private static final okhttp3.OkHttpClient HTTP=new okhttp3.OkHttpClient.Builder()
            .proxy(java.net.Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(3,java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10,java.util.concurrent.TimeUnit.SECONDS).callTimeout(90,java.util.concurrent.TimeUnit.SECONDS).build();
    private final URI endpoint;
    private final String secret;
    private final Path configFile;
    private final Supplier<AiConfig> modelConfig;
    private final Transport transport;
    private final Transport modelTransport;

    @Autowired private AiRequestAdmission admission;

    public record Response(int status, String body, String usage) {
        public Response(int status,String body) { this(status,body,null); }
    }

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
            Response response = guardedSend(endpoint.resolve("/internal/model-ping"), headers,
                    JsonUtils.write(Map.of()).getBytes(StandardCharsets.UTF_8), transport);
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
        byte[] encoded=JsonUtils.write(body).getBytes(StandardCharsets.UTF_8);
        if(encoded.length>524288)throw new IllegalArgumentException("AI请求资料过大，请缩小范围");
        var permit = begin(headers, path);
        if(permit!=null)org.slf4j.MDC.put("aiCallId",permit.id());
        var call=newCall(endpoint.resolve(path),headers,encoded,STREAM_READ_TIMEOUT_MILLIS);
        var owner=Thread.currentThread();
        var watchdog=STREAM_WATCHDOG.scheduleAtFixedRate(()->{if(owner.isInterrupted())call.cancel();},100,100,java.util.concurrent.TimeUnit.MILLISECONDS);
        boolean[] done={false};String[] usage={null};String[] outcome={"UNKNOWN"};
        try(var response=call.execute()) {
            if(response.code()!=200){outcome[0]=response.code()>=500?"UPSTREAM_ERROR":"REJECTED";throw new IllegalStateException(failure(response.code(),read(response.body()==null?null:response.body().byteStream(),2000)));}
            if(response.body()==null)throw new IOException("AI服务返回空响应");
            try(var reader=new BufferedReader(response.body().charStream())) {
                String line;
                while((line=readLineBounded(reader))!=null) {
                    if(owner.isInterrupted())throw new IOException("AI请求已取消");
                    if(!line.isBlank())dispatch(line,(type,content)->{
                        if("usage".equals(type))usage[0]=content;
                        else if(done[0])throw new IllegalStateException("AI流在完成后返回额外内容");
                        else if("upstream_error".equals(type)){outcome[0]="UPSTREAM_ERROR";listener.onEvent("error",content);}
                        else {if("error".equals(type))outcome[0]="ERROR";if("done".equals(type))done[0]=true;listener.onEvent(type,content);}
                    });
                }
                if("UNKNOWN".equals(outcome[0]) && !done[0]){outcome[0]="TRUNCATED";throw new IllegalStateException("AI输出未完成，请重试");}
                if("UNKNOWN".equals(outcome[0]))outcome[0]="COMPLETED";
            }
        } catch(IOException error) {
            outcome[0]=owner.isInterrupted()?"CANCELLED":"UPSTREAM_ERROR";
            throw new IllegalStateException("AI服务响应中断",error);
        } finally {
            watchdog.cancel(false);call.cancel();
            try {settle(permit,usage[0],outcome[0]);} finally {org.slf4j.MDC.remove("aiCallId");}
        }
    }

    static String readLineBounded(java.io.Reader reader) throws IOException {
        var line=new StringBuilder();int value;
        while((value=reader.read())!=-1){if(value=='\n')break;if(value!='\r')line.append((char)value);if(line.length()>65536)throw new IOException("AI流式事件过大");}
        return value==-1 && line.isEmpty()?null:line.toString();
    }

    /** 严格解析 NDJSON 事件，禁止将错误页或调试文本当作模型答案。 */
    static void dispatch(String line, StreamListener listener) {
        String type = "message";
        String content = line;
        try {
            JsonNode node = JsonUtils.readTree(line);
            String parsedType = JsonUtils.text(node, "type");
            if (parsedType != null && !parsedType.isBlank()) type = parsedType;
            String parsedContent = JsonUtils.text(node, "content");
            content = parsedContent == null ? "" : parsedContent;
            if("error".equals(type) && "UPSTREAM".equals(JsonUtils.text(node,"code")))type="upstream_error";
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("AI服务返回无效流式事件",invalid);
        }
        listener.onEvent(type, content);
    }

    private JsonNode send(Object facts, URI target, Transport transport, String toolToken) {
        AgentHeaders headers = callHeaders(toolToken);
        requireUsable(headers);
        try {
            Response response = guardedSend(target, headers, JsonUtils.write(facts).getBytes(StandardCharsets.UTF_8), transport);
            if (response.status() != 200 || response.body().length() > 1000000) {
                throw new IllegalStateException(failure(response.status(), response.body()));
            }
            return JsonUtils.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("无法连接本机 AI 服务", e);
        }
    }

    private boolean isRuleOnly(URI target,byte[] body) {
        if(!java.util.Set.of("/internal/inventory-analysis","/internal/management-analysis","/internal/marketing-analysis","/internal/risk-analysis").contains(target.getPath()))return false;
        var facts=JsonUtils.readTree(new String(body,StandardCharsets.UTF_8));
        var useModel=facts.get("useModel");
        return facts.isObject() && (useModel==null || useModel.isBoolean() && !useModel.asBoolean());
    }

    private AiRequestAdmission.Permit begin(AgentHeaders headers,String path) {
        if (admission == null || path.contains("inventory-review/sync")) return null;
        return admission.begin(headers.toolToken(),path,headers.modelName());
    }

    private Response guardedSend(URI target,AgentHeaders headers,byte[] body,Transport selected) throws IOException {
        if(body.length>524288)throw new IllegalArgumentException("AI请求资料过大，请缩小范围");
        var permit=isRuleOnly(target,body)?null:begin(headers,target.getPath());
        if(permit!=null)org.slf4j.MDC.put("aiCallId",permit.id());
        long started=System.nanoTime();
        String usage=null;
        String outcome="UNKNOWN";
        try {
            var response=selected.send(target,headers,body);
            usage=response.usage();outcome=response.status()==200?"COMPLETED":response.status()>=500?"UPSTREAM_ERROR":"REJECTED";
            if(response.status()==200) {
                var data=JsonUtils.readTree(response.body());
                if("unavailable".equals(JsonUtils.text(data,"modelStatus")) || "unavailable".equals(JsonUtils.text(data,"status")))outcome="UPSTREAM_ERROR";
            }
            return response;
        } catch(IOException error) {outcome="UPSTREAM_ERROR";throw error;}
        finally {try {settle(permit,usage,outcome);} finally {LOG.info("AI call requestId={} path={} elapsedMs={} outcome={}",permit==null?org.slf4j.MDC.get("requestId"):permit.id(),target.getPath(),(System.nanoTime()-started)/1_000_000,outcome);org.slf4j.MDC.remove("aiCallId");}}
    }
    private void settle(AiRequestAdmission.Permit permit,String usage,String outcome) {
        if(permit==null)return;
        Long input=null,output=null;boolean known=false;
        if(usage!=null)try {
            var node=JsonUtils.readTree(usage);
            known=node.path("known").asBoolean(false);
            if(node.path("inputTokens").isIntegralNumber() && node.path("outputTokens").isIntegralNumber()) {
                input=node.path("inputTokens").asLong();output=node.path("outputTokens").asLong();
            }
        } catch(RuntimeException ignored) { known=false; }
        admission.finish(permit,input,output,known,outcome);
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

    private static okhttp3.Call newCall(URI uri,AgentHeaders headers,byte[] body,int readTimeoutMillis) {
        var request=new okhttp3.Request.Builder().url(uri.toString())
                .post(okhttp3.RequestBody.create(new String(body,StandardCharsets.UTF_8),okhttp3.MediaType.get("application/json; charset=utf-8")))
                .header("X-Agent-Secret",headers.secret()).header("X-AI-Usage","1");
        String trace=org.slf4j.MDC.get("aiCallId");if(trace==null)trace=org.slf4j.MDC.get("requestId");
        if(trace!=null)request.header("X-Request-ID",trace);
        if(!headers.modelApiKey().isEmpty())request.header("X-Model-Api-Key",headers.modelApiKey());
        if(!headers.modelBaseUrl().isEmpty())request.header("X-Model-Base-Url",headers.modelBaseUrl());
        if(!headers.modelName().isEmpty())request.header("X-Model-Name",headers.modelName());
        if(!headers.toolToken().isEmpty())request.header("X-Tool-Token",headers.toolToken());
        return HTTP.newBuilder().readTimeout(readTimeoutMillis,java.util.concurrent.TimeUnit.MILLISECONDS).build().newCall(request.build());
    }
    private static Response post(URI uri,AgentHeaders headers,byte[] body,int readTimeoutMillis)throws IOException {
        var call=newCall(uri,headers,body,readTimeoutMillis);var owner=Thread.currentThread();
        var watchdog=STREAM_WATCHDOG.scheduleAtFixedRate(()->{if(owner.isInterrupted())call.cancel();},100,100,java.util.concurrent.TimeUnit.MILLISECONDS);
        try(var response=call.execute()) {
            String payload=read(response.body()==null?null:response.body().byteStream(),1000001);
            if(payload.length()>1000000)throw new IOException("AI服务响应过大");
            return new Response(response.code(),payload,response.header("X-AI-Usage"));
        } finally {watchdog.cancel(false);call.cancel();}
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
