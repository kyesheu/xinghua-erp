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
            只依据本次工具返回的资料回答，注明查询资源、实际覆盖的时间范围与可见业务范围。
            工具列表有条数上限，是受限样本，不代表全量；只能称为“本次返回记录中的统计”，不得推断全平台总量、完整排名或全部异常数。
            没有明确统计口径、币种或时间范围时说明限制或澄清；没有数据不等于业务为零，工具失败不能编造数值。
            区分工具提供的事实、你对样本的计算和运营推断。需要全量汇总却只有列表时，明确无法据此计算全量结果，引导使用授权业务汇总页面。
            """;

    /** 兜底文案：只说清下一步该做什么，不假装自己查过数据。 */
    private static final String FALLBACK = "AI 服务暂时不可用（可能未启动或网络中断）。"
            + "你可以先到订单、库存、售后等业务页面查询；启动 AI 服务后重新发送即可继续对话。";

    public void streamResponse(SseEmitter emitter, String sessionId, Long userId) {
        List<AiConversationHistory> history = historyService.getOwnedConversation(sessionId,userId);
        List<Map<String, String>> messages = new ArrayList<>();
        int remaining=8000;
        for(int i=history.size()-1;i>=0 && remaining>0;i--) {
            var msg=history.get(i);
            String role="user".equals(msg.getRole())?"user":"assistant";
            String content=msg.getContent()==null?"":msg.getContent();
            content=content.substring(0,Math.min(content.length(),remaining));
            remaining-=content.length();
            if(!content.isEmpty())messages.add(0,Map.of("role",role,"content",content));
        }

        executor.submit(emitter, () -> {
            StringBuilder fullResponse = new StringBuilder();
            var rejected = new java.util.concurrent.atomic.AtomicBoolean();
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("system", SYSTEM_PROMPT);
                body.put("messages", messages);
                python.stream("/internal/chat", body, toolToken.issue(userId), (type, content) -> {
                    switch (type) {
                        case "message" -> {
                            if(fullResponse.length()+content.length()>32000)throw new IllegalStateException("AI输出超过限制");
                            fullResponse.append(content);
                            sendJsonEvent(emitter, "message", content);
                        }
                        case "error" -> {rejected.set(true);sendJsonEvent(emitter, "error", content);}
                        default -> { }
                    }
                });
                if (!fullResponse.isEmpty() && !rejected.get()) {
                    historyService.saveMessage(sessionId, userId, "assistant", fullResponse.toString());
                }
                if(!rejected.get())sendJsonEvent(emitter, "done", "");
            } catch (Exception e) {
                log.warn("AI 对话调用失败，返回兜底提示: {}", e.getMessage());
                sendJsonEvent(emitter, "error", e instanceof cn.xinghuaerp.ai.service.AiAdmissionRejected ? e.getMessage() : FALLBACK);
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
