package cn.xinghuaerp.order.mq;

import cn.xinghuaerp.json.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDateTime;
import java.util.UUID;

/** 与新订单同事务记录事件；事务外调用直接拒绝，避免双写窗口。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.mq.enabled", havingValue = "true")
public class OrderEventPublisher {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishOrderCreated(Long shopOrderId, Long orderId) {
        if (shopOrderId == null || shopOrderId <= 0 || orderId == null || orderId <= 0)
            throw new IllegalArgumentException("订单事件缺少有效订单标识");
        var event = new OrderCreatedEvent(UUID.randomUUID().toString(), shopOrderId, orderId,
                LocalDateTime.now().toString());
        jdbc.update("INSERT INTO order_event_outbox(message_id, order_id, payload) VALUES (?, ?, ?)",
                event.messageId(), orderId, JsonUtils.write(event));
    }
}
