package cn.xinghuaerp.order.mq;

import cn.xinghuaerp.enums.EnumShopType;
import cn.xinghuaerp.sse.SseService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCommittedNotifierTest {
    private void exercise(boolean rollback, boolean fail) {
        var sse = mock(SseService.class);
        if (fail) when(sse.broadcastNewOrderMessage(any(),any())).thenThrow(new IllegalStateException("disconnected"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SseService.class, () -> sse);
            context.registerBean(TransactionalEventListenerFactory.class);
            context.register(OrderCommittedNotifier.class);
            context.refresh();
            var dataSource = new JdbcDataSource(); dataSource.setURL("jdbc:h2:mem:notification");
            var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            assertDoesNotThrow(() -> tx.executeWithoutResult(status -> {
                context.publishEvent(new OrderCommittedNotification(EnumShopType.TAO,"ORDER-1"));
                verifyNoInteractions(sse);
                if (rollback) status.setRollbackOnly();
            }));
            if (rollback) verifyNoInteractions(sse);
            else verify(sse).broadcastNewOrderMessage(EnumShopType.TAO,"ORDER-1");
        }
    }
    @Test void sendsOnlyAfterCommit() { exercise(false,false); }
    @Test void rollbackDoesNotNotify() { exercise(true,false); }
    @Test void brokenConnectionDoesNotFailCommittedOrder() { exercise(false,true); }
    @Test void nonTransactionalEventIsIgnored() {
        var sse = mock(SseService.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SseService.class, () -> sse);
            context.registerBean(TransactionalEventListenerFactory.class);
            context.register(OrderCommittedNotifier.class); context.refresh();
            context.publishEvent(new OrderCommittedNotification(EnumShopType.TAO,"ORDER-1"));
            verifyNoInteractions(sse);
        }
    }
}
