package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.ai.mapper.AiConversationHistoryMapper;
import cn.xinghuaerp.ai.entity.AiConversationHistory;
import cn.xinghuaerp.ai.service.AiConversationHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class AiConversationHistoryServiceImpl
        extends ServiceImpl<AiConversationHistoryMapper, AiConversationHistory>
        implements AiConversationHistoryService {

    private final cn.xinghuaerp.ai.service.AiSessionAccess access;

    @Override
    public List<AiConversationHistory> getOwnedConversation(String sessionId, Long userId) {
        return access.recent(sessionId,userId,null,null,40);
    }
    @Override
    public List<AiConversationHistory> getConversationPage(String sessionId, Long userId, Long beforeTimestamp, Long beforeId, int limit) {
        return access.recent(sessionId,userId,beforeTimestamp,beforeId,limit);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void saveMessage(String sessionId, Long userId, String role, String content) {
        access.claim(sessionId,userId);
        AiConversationHistory msg = new AiConversationHistory();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setCreateTime(LocalDateTime.now());
        msg.setUpdateTime(LocalDateTime.now());
        if (!save(msg)) throw new IllegalStateException("保存会话消息失败");
    }

    @Override
    public List<String> getSessions(Long userId) {
        return access.sessions(userId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId, Long userId) {
        if(!access.lockForDelete(sessionId,userId))return;
        lambdaUpdate()
                .eq(AiConversationHistory::getSessionId, sessionId)
                .eq(AiConversationHistory::getUserId, userId)
                .remove();
    }
}
