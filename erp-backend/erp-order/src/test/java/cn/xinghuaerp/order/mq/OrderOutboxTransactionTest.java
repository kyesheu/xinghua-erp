package cn.xinghuaerp.order.mq;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.aop.framework.ProxyFactory;
import static org.junit.jupiter.api.Assertions.*;

class OrderOutboxTransactionTest {
    @Test void businessAndOutboxCommitAndRollbackTogether() {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:outbox_tx;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE order_event_outbox(message_id varchar(36) PRIMARY KEY,order_id bigint UNIQUE,payload text)");
        jdbc.execute("CREATE TABLE test_orders(id bigint PRIMARY KEY)");
        var manager = new DataSourceTransactionManager(source);
        var proxy = new ProxyFactory(new OrderEventPublisher(jdbc));
        proxy.setProxyTargetClass(true);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(interceptor);
        var publisher = (OrderEventPublisher) proxy.getProxy();
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> publisher.publishOrderCreated(1L,1L));
        var tx = new TransactionTemplate(manager);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            jdbc.update("INSERT INTO test_orders VALUES(1)");
            publisher.publishOrderCreated(1L,1L);
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_orders",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_event_outbox",Integer.class));
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO test_orders VALUES(1)"); publisher.publishOrderCreated(1L,1L);
        });
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_orders",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_event_outbox",Integer.class));
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO test_orders VALUES(2)"); publisher.publishOrderCreated(2L,1L);
        }));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_orders",Integer.class));
    }
}
