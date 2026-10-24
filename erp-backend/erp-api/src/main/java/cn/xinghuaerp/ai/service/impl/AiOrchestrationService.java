package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.ai.entity.AiConfig;
import cn.xinghuaerp.ai.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
@Service
public class AiOrchestrationService {

    /** 模型型分析接口的读超时。 */
    private static final int CHAT_TIMEOUT_SECONDS = 120;
    /** 测试连接只需确认凭据可用，不宜让使用者久等。 */
    private static final int TEST_TIMEOUT_SECONDS = 15;

    private final AiConfigService aiConfigService;

    public AiConfig getDefaultConfig() {
        return aiConfigService.getDefaultConfig();
    }

    /**
     * 用配置本身发一次最小请求，验证 API 地址、密钥与模型名是否可用。
     * 调用方负责把异常转成可读提示，这里只抛出原始异常。
     */
    public String testConnection(AiConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在");
        }
        String reply = build(config, TEST_TIMEOUT_SECONDS, new Object[0]).prompt().user("ping").call().content();
        return reply == null ? "" : reply;
    }

    public ChatClient buildChatClient(AiConfig config, Object... tools) {
        return build(config, CHAT_TIMEOUT_SECONDS, tools);
    }

    private ChatClient build(AiConfig config, int timeoutSeconds, Object[] tools) {
        if (config == null) {
            throw new IllegalStateException("未配置已启用的默认AI模型，请在 AI 智能 > 模型配置 中启用并设为默认");
        }
        if (!StringUtils.hasText(config.getApiEndpoint())
                || !StringUtils.hasText(config.getApiKey())
                || !StringUtils.hasText(config.getModelName())) {
            throw new IllegalStateException("AI模型配置不完整，请填写API地址、API密钥和模型名称");
        }
        String baseUrl = config.getApiEndpoint()
                .replaceAll("/v1/?$", "")
                .replaceAll("/+$", "");

        var httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds));
        var factory = new ReactorClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        DeepSeekApi api = DeepSeekApi.builder()
                .baseUrl(baseUrl)
                .apiKey(new SimpleApiKey(config.getApiKey()))
                .completionsPath("/v1/chat/completions")
                .restClientBuilder(RestClient.builder().requestFactory(factory))
                .build();

        DeepSeekChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(DeepSeekChatOptions.builder()
                        .model(config.getModelName())
                        .build())
                .build();

        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (tools.length > 0) {
            builder.defaultTools(tools);
        }
        return builder.build();
    }

    public ChatClient buildDefaultChatClient(Object... tools) {
        AiConfig config = getDefaultConfig();
        if (config == null) {
            throw new RuntimeException("未配置默认AI模型，请先在AI配置中设置默认模型");
        }
        return buildChatClient(config, tools);
    }
}
