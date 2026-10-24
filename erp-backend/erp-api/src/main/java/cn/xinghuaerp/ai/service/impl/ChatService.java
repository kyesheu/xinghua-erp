package cn.xinghuaerp.ai.service.impl;

import tools.jackson.databind.node.ObjectNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.ai.entity.AiConversationHistory;
import cn.xinghuaerp.ai.service.AiConversationHistoryService;
import cn.xinghuaerp.ai.service.AiTaskExecutor;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.ai.service.ToolToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话服务：本类只负责组装上下文与转发事件，模型调用与工具循环都在 Python 服务里。
 *
 * <p>AI 服务不可用时给确定的兜底提示，而不是让前端停在空白流上。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ChatService {

    private final AiConversationHistoryService historyService;
    private final PythonAgentClient python;
    private final ToolToken toolToken;
    private final AiTaskExecutor executor;

    private static final String SYSTEM_PROMPT = """
            你是兴华电商ERP系统的AI助手，帮助用户处理电商运营、订单管理、商品管理、库存管理、采购管理、仓库管理、售后管理等方面的问题。请用专业、简洁的中文回答。

            你可以多次调用工具来逐步获取数据，每次根据返回的结果决定下一步查什么。
            所有工具的说明和参数已内置，由你自行决定每次调哪些工具、需要什么筛选条件。
            由你自行对数据进行分组、排序、统计、对比，得出结论。
            """;

    /** 兜底文案：只说清下一步该做什么，不假装自己查过数据。 */
    private static final String FALLBACK = "AI 服务暂时不可用（可能未启动或网络中断）。"
            + "你可以先到订单、库存、售后等业务页面查询；启动 AI 服务后重新发送即可继续对话。";

    public void streamResponse(SseEmitter emitter, String sessionId, Long userId) {
        List<AiConversationHistory> history = historyService.getConversation(sessionId);
        List<Map<String, String>> messages = new ArrayList<>();
        for (AiConversationHistory msg : history) {
            String role = "user".equals(msg.getRole()) ? "user" : "assistant";
            messages.add(Map.of("role", role, "content", msg.getContent() == null ? "" : msg.getContent()));
        }

        executor.submit(() -> {
            StringBuilder fullResponse = new StringBuilder();
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("system", SYSTEM_PROMPT);
                body.put("messages", messages);
                python.stream("/internal/chat", body, toolToken.issue(userId), (type, content) -> {
                    switch (type) {
                        case "message" -> {
                            fullResponse.append(content);
                            sendJsonEvent(emitter, "message", content);
                        }
                        case "error" -> sendJsonEvent(emitter, "error", content);
                        default -> { }
                    }
                });
                if (!fullResponse.isEmpty()) {
                    historyService.saveMessage(sessionId, userId, "assistant", fullResponse.toString());
                }
                sendJsonEvent(emitter, "done", "");
            } catch (Exception e) {
                log.warn("AI 对话调用失败，返回兜底提示: {}", e.getMessage());
                sendJsonEvent(emitter, "error", FALLBACK);
            } finally {
                safeComplete(emitter);
            }
        });
    }

    private void sendJsonEvent(SseEmitter emitter, String type, String content) {
        try {
            ObjectNode event = JsonUtils.object();
            event.put("type", type);
            if (content != null) {
                event.put("content", content);
            }
            emitter.send(SseEmitter.event().data(event.toString()));
        } catch (IOException e) {
            log.debug("发送SSE事件失败: {}", e.getMessage());
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}
