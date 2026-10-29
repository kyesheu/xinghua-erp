package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.ai.entity.AiConversationHistory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AiConversationHistoryService extends IService<AiConversationHistory> {

    List<AiConversationHistory> getOwnedConversation(String sessionId, Long userId);
    List<AiConversationHistory> getConversationPage(String sessionId, Long userId, Long beforeTimestamp, Long beforeId, int limit);
    void saveMessage(String sessionId, Long userId, String role, String content);
    List<String> getSessions(Long userId);
    void deleteSession(String sessionId, Long userId);
}
