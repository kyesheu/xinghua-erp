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

/** 短事务领取租约，网络发送不占用数据库事务；过期重领允许重复投递。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.mq.enabled", havingValue = "true")
public class OrderOutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbitTemplate;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    record Claimed(String id,String payload,String lease) {}

    Claimed claim() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            var rows=jdbc.queryForList("SELECT message_id,payload FROM order_event_outbox WHERE sent_at IS NULL AND next_attempt_at<=CURRENT_TIMESTAMP AND (lease_until IS NULL OR lease_until<=CURRENT_TIMESTAMP) ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED");
            if(rows.isEmpty())return null;
            String id=(String)rows.get(0).get("message_id");String lease=java.util.UUID.randomUUID().toString();
            jdbc.update("UPDATE order_event_outbox SET lease_token=?,lease_until=? WHERE message_id=?",lease,java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(30)),id);
            return new Claimed(id,rows.get(0).get("payload").toString(),lease);
        });
    }

    @Scheduled(fixedDelayString = "${erp.mq.outbox-delay-ms:5000}")
    public void dispatch() {
        for(int i=0;i<20 && !Thread.currentThread().isInterrupted();i++) {
            var event=claim();if(event==null)break;
            try {
                var correlation=new CorrelationData(event.id());
                rabbitTemplate.convertAndSend(OrderMqConfig.ORDER_EXCHANGE,OrderMqConfig.ORDER_CREATED_ROUTING_KEY,event.payload(),message->{
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(event.id());return message;
                },correlation);
                var confirm=correlation.getFuture().get(5,TimeUnit.SECONDS);
                if(!confirm.ack() || correlation.getReturned()!=null)throw new IllegalStateException("消息未确认或未路由");
                jdbc.update("UPDATE order_event_outbox SET sent_at=CURRENT_TIMESTAMP,lease_until=NULL,lease_token=NULL WHERE message_id=? AND lease_token=? AND sent_at IS NULL",event.id(),event.lease());
            } catch(Exception error) {
                if(error instanceof InterruptedException)Thread.currentThread().interrupt();
                jdbc.update("UPDATE order_event_outbox SET attempts=attempts+1,next_attempt_at=?,lease_until=NULL,lease_token=NULL WHERE message_id=? AND lease_token=? AND sent_at IS NULL",java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(30)),event.id(),event.lease());
                log.warn("订单事件投递失败，保留待重试：messageId={} errorType={}",event.id(),error.getClass().getSimpleName());
            }
        }
    }
}
