package cn.xinghuaerp.order.facade;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 订单通知的只读观测与有界清理，不提供任意重放已发送事件的接口。 */
@Component
@RequiredArgsConstructor
public class OrderNotificationOperations {
    private final JdbcTemplate jdbc;
    public java.util.Map<String,Object> summary() {
        return jdbc.queryForMap("SELECT COUNT(*) AS pending,MIN(created_at) AS oldestPending,COALESCE(MAX(attempts),0) AS maxAttempts FROM order_event_outbox WHERE sent_at IS NULL");
    }
    public int pruneSent() {
        return jdbc.update("DELETE FROM order_event_outbox WHERE sent_at IS NOT NULL AND sent_at<? LIMIT 500",java.sql.Timestamp.from(java.time.Instant.now().minus(30,java.time.temporal.ChronoUnit.DAYS)));
    }
}
