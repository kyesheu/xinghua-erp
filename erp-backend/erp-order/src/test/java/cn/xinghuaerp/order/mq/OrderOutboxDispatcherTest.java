package cn.xinghuaerp.order.mq;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderOutboxDispatcherTest {
    private void exercise(boolean ack, boolean returned) {
        var jdbc = mock(JdbcTemplate.class);
        var rabbit = mock(RabbitTemplate.class);
        var tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForList(anyString())).thenReturn(
                List.of(Map.of("message_id", (Object)"event-1", "payload", "{}")), List.of());
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            if (returned) correlation.setReturned(new org.springframework.amqp.core.ReturnedMessage(
                    new org.springframework.amqp.core.Message(new byte[0]),312,"NO_ROUTE","erp.order","order.created"));
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, "test"));
            return null;
        }).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
        new OrderOutboxDispatcher(jdbc,rabbit,tm).dispatch();
        if (ack && !returned) {
            verify(jdbc).update(contains("SET sent_at="),eq("event-1"));
            verify(jdbc,never()).update(contains("attempts=attempts+1"),any(Object.class));
        } else {
            verify(jdbc,never()).update(contains("SET sent_at="),any(Object.class));
            verify(jdbc).update(contains("attempts=attempts+1"),eq("event-1"));
        }
        verify(tm,atLeastOnce()).commit(any());
    }
    @Test void confirmedMessageMarkedSent() { exercise(true,false); }
    @Test void brokerNackRetainsEventForRetry() { exercise(false,false); }
    @Test void unroutableMessageRetainsEventEvenWhenAcked() { exercise(true,true); }
    @Test void interruptPreservesRetryAndStopsTheBatch() {
        var jdbc = mock(JdbcTemplate.class);
        var rabbit = mock(RabbitTemplate.class);
        var tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("message_id",(Object) "interrupted", "payload","{}")));
        doAnswer(invocation -> { Thread.currentThread().interrupt(); return null; })
                .when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
        try {
            new OrderOutboxDispatcher(jdbc,rabbit,tm).dispatch();
            org.junit.jupiter.api.Assertions.assertTrue(Thread.currentThread().isInterrupted());
            verify(jdbc,times(1)).queryForList(anyString());
            verify(jdbc).update(contains("attempts=attempts+1"),eq("interrupted"));
            verify(jdbc,never()).update(contains("SET sent_at="),any(Object.class));
            verify(tm).commit(any());
        } finally { Thread.interrupted(); }
    }
}
