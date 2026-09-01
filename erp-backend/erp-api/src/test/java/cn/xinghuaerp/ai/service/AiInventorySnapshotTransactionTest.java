package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import cn.xinghuaerp.ai.mapper.AiInventoryMapper;
import cn.xinghuaerp.ai.vo.AiInventoryOverviewRow;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

class AiInventorySnapshotTransactionTest {
    @Test void riskSummaryCountAndRowsUseOneSnapshot() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE sample_sku(id BIGINT)");
        db.update("INSERT INTO sample_sku VALUES(1)");
        var mapper = mock(AiInventoryMapper.class);
        when(mapper.countOverview(7L,10L,null)).thenAnswer(call -> {
            long count = db.queryForObject("SELECT COUNT(*) FROM sample_sku",Long.class);
            var pool = Executors.newSingleThreadExecutor();
            try { pool.submit(() -> db.update("INSERT INTO sample_sku VALUES(2)")).get(5,TimeUnit.SECONDS); }
            finally { pool.shutdownNow(); }
            return count;
        });
        when(mapper.allOverview(7L,10L,null)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return db.query("SELECT id FROM sample_sku",(rs,n) -> {
                var row = new cn.xinghuaerp.ai.vo.AiInventoryOverviewRow();
                row.setSkuId(rs.getString(1)); row.setInventoryRows(0L); return row;
            });
        });
        var factory = new ProxyFactory(new AiInventoryReadService(mapper));
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        var service = (AiInventoryReadService) factory.getProxy();
        var result = service.riskSummary(new BusinessSubjectScopeService.Scope("SHOP",10L,7L,10L));
        assertEquals(1,result.total()); assertEquals(1L,result.risks().get("待核实库存"));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM sample_sku",Integer.class));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        db.execute("SHUTDOWN");
    }
    @Test void inventoryAndSalesSeeSameCommittedSnapshotAndReleaseBeforeReturn() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE sample_stock(amount INT)");
        db.update("INSERT INTO sample_stock VALUES(1)");
        var mapper = mock(AiInventoryMapper.class);
        var first = new AtomicInteger();
        when(mapper.find(anyLong(), any(), any(), any())).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            first.set(db.queryForObject("SELECT amount FROM sample_stock", Integer.class));
            var pool = Executors.newSingleThreadExecutor();
            try {
                pool.submit(() -> db.update("UPDATE sample_stock SET amount=2")).get(5, TimeUnit.SECONDS);
            } finally { pool.shutdownNow(); }
            return List.of();
        });
        when(mapper.salesQuantity(anyLong(), any(), any(), any(), any())).thenAnswer(call -> db.queryForObject("SELECT amount FROM sample_stock", Long.class));
        var target = new AiInventoryReadService(mapper);
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        var service = (AiInventoryReadService) factory.getProxy();
        var scope = new BusinessSubjectScopeService.Scope("SHOP", 10L, 7L, 10L);
        var snapshot = service.analysisSnapshot(5L, null, 7, scope, true, false);
        assertEquals(1, first.get());
        assertEquals(1, snapshot.sales().quantity());
        assertEquals(2, db.queryForObject("SELECT amount FROM sample_stock", Integer.class));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        db.execute("SHUTDOWN");
    }
}

