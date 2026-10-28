package cn.xinghuaerp.order.mq;

import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.facade.MessageFacade;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import cn.xinghuaerp.order.mq.OrderCreatedConsumer;
import cn.xinghuaerp.order.mq.OrderCreatedEvent;
import cn.xinghuaerp.order.mq.OrderEventPublisher;
import cn.xinghuaerp.order.mq.OrderMqConfig;

/**
 * 订单事件 MQ 链路的纯单元测试：不依赖 RabbitMQ Broker，也不加载 Spring 上下文。
 * 覆盖消费端手动 ACK / 幂等去重 / 死信转投与生产端发布、失败吞没。
 */
class OrderMqTest {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Message jsonMessage(OrderCreatedEvent event) {
        return new Message(JsonUtils.write(event).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private OrderCreatedEvent event() {
        return new OrderCreatedEvent("msg-1", 11L, 22L,
                LocalDateTime.now().format(DT));
    }

    @Test
    void validEventSavesMessageAndAcks() throws IOException {
        var service = mock(MessageFacade.class);
        var channel = mock(Channel.class);
        var consumer = new OrderCreatedConsumer(service);
        Message message = jsonMessage(event());

        consumer.onMessage(message, channel);

        verify(service).saveMessage(any(SysMessage.class));
        verify(channel).basicAck(0L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void brokenPayloadGoesToDeadLetter() throws IOException {
        var service = mock(MessageFacade.class);
        var channel = mock(Channel.class);
        var consumer = new OrderCreatedConsumer(service);

        consumer.onMessage(new Message("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8)), channel);

        verify(service, never()).saveMessage(any(SysMessage.class));
        verify(channel).basicNack(0L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void missingOrderIdGoesToDeadLetter() throws IOException {
        var service = mock(MessageFacade.class);
        var channel = mock(Channel.class);
        var consumer = new OrderCreatedConsumer(service);
        OrderCreatedEvent bad = new OrderCreatedEvent("msg-2", 11L, null,
                LocalDateTime.now().format(DT));

        consumer.onMessage(jsonMessage(bad), channel);

        verify(service, never()).saveMessage(any(SysMessage.class));
        verify(channel).basicNack(0L, false, false);
    }

    @Test
    void saveFailureGoesToDeadLetterInsteadOfBlocking() throws IOException {
        var service = mock(MessageFacade.class);
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(service).saveMessage(any(SysMessage.class));
        var channel = mock(Channel.class);
        var consumer = new OrderCreatedConsumer(service);

        consumer.onMessage(jsonMessage(event()), channel);

        verify(channel).basicNack(0L, false, false);
    }

    @Test
    void publisherRecordsEventForTransaction() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var publisher = new OrderEventPublisher(jdbc);
        publisher.publishOrderCreated(11L, 22L);
        verify(jdbc).update(contains("INSERT INTO order_event_outbox"), any(), eq(22L), contains("\"orderId\":22"));
    }

    @Test
    void publisherRejectsInvalidOrder() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var publisher = new OrderEventPublisher(jdbc);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> publisher.publishOrderCreated(11L, null));
    }

    @Test
    void publisherPropagatesStorageFailureToRollbackOrder() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenThrow(new RuntimeException("db down"));
        var publisher = new OrderEventPublisher(jdbc);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> publisher.publishOrderCreated(11L, 22L));
    }
}
