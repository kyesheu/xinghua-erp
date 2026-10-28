package cn.xinghuaerp.order.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.TimeUnit;

/** 锁定一条待发事件直到确认结束；宕机或确认不明时保留，允许重复投递。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.mq.enabled", havingValue = "true")
public class OrderOutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbitTemplate;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${erp.mq.outbox-delay-ms:5000}")
    public void dispatch() {
        for (int i = 0; i < 20; i++) {
            if (Thread.currentThread().isInterrupted()) break;
            Boolean sent = new TransactionTemplate(transactionManager).execute(status -> {
                var rows = jdbc.queryForList("SELECT message_id, payload FROM order_event_outbox WHERE sent_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED");
                if (rows.isEmpty()) return false;
                String id = (String) rows.get(0).get("message_id");
                try {
                    var correlation = new CorrelationData(id);
                    rabbitTemplate.convertAndSend(OrderMqConfig.ORDER_EXCHANGE,
                            OrderMqConfig.ORDER_CREATED_ROUTING_KEY, rows.get(0).get("payload").toString(), message -> {
                                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                                message.getMessageProperties().setMessageId(id);
                                return message;
                            }, correlation);
                    var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                    if (!confirm.ack() || correlation.getReturned() != null)
                        throw new IllegalStateException("消息未被交换机确认或未路由至队列");
                    jdbc.update("UPDATE order_event_outbox SET sent_at=CURRENT_TIMESTAMP WHERE message_id=?", id);
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    jdbc.update("UPDATE order_event_outbox SET attempts=attempts+1, next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 SECOND) WHERE message_id=?", id);
                    log.warn("订单事件投递失败，保留待重试：messageId={}", id, e);
                    if (Thread.currentThread().isInterrupted()) return false;
                }
                return true;
            });
            if (!Boolean.TRUE.equals(sent)) break;
        }
    }
}
