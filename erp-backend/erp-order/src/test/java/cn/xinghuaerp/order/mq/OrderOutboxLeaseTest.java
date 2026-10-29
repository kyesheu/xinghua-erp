package cn.xinghuaerp.order.mq;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessagePostProcessor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderOutboxLeaseTest {
    JdbcTemplate jdbc;RabbitTemplate rabbit;OrderOutboxDispatcher dispatcher;
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(ds);rabbit=mock(RabbitTemplate.class);
        jdbc.execute("CREATE TABLE order_event_outbox(message_id varchar(36) PRIMARY KEY,payload varchar(100),created_at timestamp DEFAULT CURRENT_TIMESTAMP,sent_at timestamp,attempts int DEFAULT 0,next_attempt_at timestamp DEFAULT CURRENT_TIMESTAMP,lease_until timestamp,lease_token varchar(36))");
        jdbc.update("INSERT INTO order_event_outbox(message_id,payload) VALUES('event','{}')");
        dispatcher=new OrderOutboxDispatcher(jdbc,rabbit,new DataSourceTransactionManager(ds));
    }
    @Test void leaseExpiresAndOldOwnerCannotMarkNewLeaseSent() {
        var first=dispatcher.claim();assertNotNull(first);assertNull(dispatcher.claim());
        jdbc.update("UPDATE order_event_outbox SET lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        var second=dispatcher.claim();assertNotEquals(first.lease(),second.lease());
        assertEquals(0,jdbc.update("UPDATE order_event_outbox SET sent_at=CURRENT_TIMESTAMP WHERE message_id=? AND lease_token=?",first.id(),first.lease()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_event_outbox WHERE sent_at IS NULL",Integer.class));
    }
    @Test void networkConfirmationDoesNotHoldDatabaseTransaction() {
        doAnswer(call->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            ((CorrelationData)call.getArgument(4)).getFuture().complete(new CorrelationData.Confirm(true,null));return null;
        }).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
        dispatcher.dispatch();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_event_outbox WHERE sent_at IS NOT NULL",Integer.class));
    }
    @Test void cleanupNeverRemovesPendingEvents() {
        jdbc.update("UPDATE order_event_outbox SET created_at=DATEADD('DAY',-100,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO order_event_outbox(message_id,payload,sent_at) VALUES('old','{}',DATEADD('DAY',-31,CURRENT_TIMESTAMP))");
        assertEquals(1,new cn.xinghuaerp.order.facade.OrderNotificationOperations(jdbc).pruneSent());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_event_outbox WHERE sent_at IS NULL",Integer.class));
    }
}
