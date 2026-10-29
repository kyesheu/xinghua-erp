package cn.xinghuaerp.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 会话标识为全局唯一，所有写入先在同一业务事务中锁定归属。 */
@Service
@RequiredArgsConstructor
public class AiSessionAccess {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(String sessionId, Long userId) {
        validate(sessionId,userId);
        jdbc.update("INSERT INTO ai_conversation_session(session_id,user_id) VALUES (?,?) ON DUPLICATE KEY UPDATE session_id=session_id", sessionId,userId);
        var owners=jdbc.queryForList("SELECT user_id FROM ai_conversation_session WHERE session_id=? FOR UPDATE", sessionId);
        if (owners.size()!=1 || ((Number)owners.get(0).get("user_id")).longValue()!=userId) deny();
        assertLegacyOwner(sessionId,userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockForDelete(String sessionId,Long userId) {
        assertOwner(sessionId,userId);
        var rows=jdbc.queryForList("SELECT user_id FROM ai_conversation_session WHERE session_id=? FOR UPDATE",sessionId);
        if(!rows.isEmpty()) {
            if(((Number)rows.get(0).get("user_id")).longValue()!=userId)deny();
            return true;
        }
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_history WHERE session_id=?",Long.class,sessionId);
        if(count!=null && count>0){claim(sessionId,userId);return true;}
        return false;
    }

    public void assertOwner(String sessionId, Long userId) {
        validate(sessionId,userId);
        var owners=jdbc.queryForList("SELECT user_id FROM ai_conversation_session WHERE session_id=?",sessionId);
        if (!owners.isEmpty() && ((Number)owners.get(0).get("user_id")).longValue()!=userId) deny();
        assertLegacyOwner(sessionId,userId);
    }

    public java.util.List<cn.xinghuaerp.ai.entity.AiConversationHistory> recent(String sessionId, Long userId, Long beforeTimestamp, Long beforeId, int limit) {
        validate(sessionId,userId);
        if (limit<1 || limit>100 || (beforeTimestamp==null)!=(beforeId==null)) throw new IllegalArgumentException("历史分页参数无效");
        assertOwner(sessionId,userId);
        String sql="SELECT * FROM ai_conversation_history WHERE session_id=? AND user_id=?";
        var args=new java.util.ArrayList<Object>();args.add(sessionId);args.add(userId);
        if(beforeTimestamp!=null) { sql+=" AND (timestamp<? OR (timestamp=? AND id<?))"; args.add(beforeTimestamp);args.add(beforeTimestamp);args.add(beforeId); }
        sql+=" ORDER BY timestamp DESC,id DESC LIMIT ?";args.add(limit);
        var rows=jdbc.query(sql,new org.springframework.jdbc.core.BeanPropertyRowMapper<>(cn.xinghuaerp.ai.entity.AiConversationHistory.class),args.toArray());
        java.util.Collections.reverse(rows);
        return rows;
    }

    public java.util.List<String> sessions(Long userId) {
        if(userId==null)throw new IllegalArgumentException("用户无效");
        return jdbc.queryForList("SELECT h.session_id FROM ai_conversation_history h WHERE h.user_id=? AND NOT EXISTS (SELECT 1 FROM ai_conversation_history other WHERE other.session_id=h.session_id AND (other.user_id IS NULL OR other.user_id<>?)) GROUP BY h.session_id ORDER BY MAX(h.timestamp) DESC,MAX(h.id) DESC LIMIT 100",String.class,userId,userId);
    }

    private void assertLegacyOwner(String sessionId, Long userId) {
        Long foreign=jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_history WHERE session_id=? AND (user_id IS NULL OR user_id<>?)",Long.class,sessionId,userId);
        if (foreign==null || foreign>0) deny();
    }
    private void validate(String id,Long user) {
        if (id==null || id.isBlank() || id.length()>64 || user==null) throw new IllegalArgumentException("会话与用户标识无效");
    }
    private void deny() { throw new AccessDeniedException("无权访问该会话"); }
}
