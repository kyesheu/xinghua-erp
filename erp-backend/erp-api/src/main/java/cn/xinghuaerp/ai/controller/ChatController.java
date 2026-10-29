package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.ai.service.impl.ChatService;
import cn.xinghuaerp.ai.entity.AiConfig;
import cn.xinghuaerp.ai.entity.AiConversationHistory;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.ai.service.AiConfigService;
import cn.xinghuaerp.ai.service.AiConversationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/ai")
public class ChatController extends BaseController {

    private final ChatService chatService;
    private final AiConfigService aiConfigService;
    private final AiConversationHistoryService historyService;

    @PostMapping(value = "/chat/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@RequestBody ChatSendRequest req) {
        if (req == null || req.getMessage() == null || req.getMessage().isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (req.getMessage().length() > 8000) {
            throw new IllegalArgumentException("消息不能超过8000个字符");
        }
        if (req.getSessionId() != null && req.getSessionId().length() > 64) {
            throw new IllegalArgumentException("会话标识不能超过64个字符");
        }
        String sessionId = req.getSessionId() != null && !req.getSessionId().isEmpty()
                ? req.getSessionId() : UUID.randomUUID().toString();
        Long userId = getUserId();

        historyService.saveMessage(sessionId, userId, "user", req.getMessage());

        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return errorEmitter("请先在 AI 智能 > 模型配置 中添加并启用默认模型");
        }

        SseEmitter emitter = new SseEmitter(180000L);
        chatService.streamResponse(emitter, sessionId, userId);
        return emitter;
    }

    @GetMapping("/chat/history")
    public AjaxResult getHistory(@RequestParam String sessionId,
                                 @RequestParam(required=false) Long beforeTimestamp,
                                 @RequestParam(required=false) Long beforeId,
                                 @RequestParam(defaultValue="50") int limit) {
        List<AiConversationHistory> list = historyService.getConversationPage(sessionId,getUserId(),beforeTimestamp,beforeId,limit);
        return AjaxResult.success(list);
    }

    @GetMapping("/chat/sessions")
    public AjaxResult getSessions() {
        List<String> sessions = historyService.getSessions(getUserId());
        return AjaxResult.success(sessions);
    }

    @DeleteMapping("/chat/session/{sessionId}")
    public AjaxResult deleteSession(@PathVariable String sessionId) {
        historyService.deleteSession(sessionId, getUserId());
        return AjaxResult.success();
    }

    private SseEmitter errorEmitter(String msg) {
        SseEmitter emitter = new SseEmitter();
        try {
            emitter.send(SseEmitter.event().data(
                    "{\"type\":\"error\",\"content\":\"" + msg + "\"}"));
            emitter.complete();
        } catch (Exception ignored) {}
        return emitter;
    }

    public static class ChatSendRequest {
        private String sessionId;
        private String message;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
