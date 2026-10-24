package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.ai.entity.AiConfig;
import cn.xinghuaerp.ai.service.AiConfigService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.json.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 模型编排的边界层。
 *
 * <p>模型构建、工具循环、流式输出全部在 Python 服务里完成，本类不再持有任何 ChatClient。
 * 保留的职责只有两件：取页面配置的默认模型，以及转发「测试连接」。</p>
 */
@Service
public class AiOrchestrationService {

    private final AiConfigService aiConfigService;
    private final PythonAgentClient python;

    public AiOrchestrationService(AiConfigService aiConfigService, PythonAgentClient python) {
        this.aiConfigService = aiConfigService;
        this.python = python;
    }

    public AiConfig getDefaultConfig() {
        return aiConfigService.getDefaultConfig();
    }

    /**
     * 用页面保存的配置做一次最小请求，验证地址、密钥与模型名是否可用。
     * 真正的调用发生在 Python 服务里，这里只负责转发与把错误转成可读提示。
     */
    public String testConnection(AiConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在");
        }
        if (!StringUtils.hasText(config.getApiEndpoint())
                || !StringUtils.hasText(config.getApiKey())
                || !StringUtils.hasText(config.getModelName())) {
            throw new IllegalStateException("AI模型配置不完整，请填写API地址、API密钥和模型名称");
        }
        JsonNode reply = python.modelPing(config, "");
        String text = reply == null ? null : JsonUtils.text(reply, "reply");
        return text == null ? "" : text;
    }
}
