package cn.xinghuaerp.purchase.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseOrderMapper;
import cn.xinghuaerp.purchase.entity.ErpPurchaseOrder;
import cn.xinghuaerp.purchase.request.PurchaseOrderOptionRequest;
import cn.xinghuaerp.purchase.service.impl.ErpPurchaseOrderServiceImpl;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Real mapper checks against isolated H2; never approves a real purchase order. */
class PurchaseAuditTest {
    private JdbcTemplate db;
    private ErpPurchaseOrderServiceImpl service;

    @BeforeEach
    void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(ds);
        var config = new MybatisConfiguration();
        config.addMapper(ErpPurchaseOrderMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        var info = TableInfoHelper.getTableInfo(ErpPurchaseOrder.class);
        var columns = info.getFieldList().stream().map(f -> f.getColumn() + " " + sqlType(f.getPropertyType()))
                .collect(Collectors.joining(","));
        db.execute("CREATE TABLE erp_purchase_order (id bigint primary key," + columns + ")");
        db.update("INSERT INTO erp_purchase_order(id,status) VALUES(1,0)");
        service = new ErpPurchaseOrderServiceImpl(session.getMapper(ErpPurchaseOrderMapper.class), null, null, null, null, null);
    }

    @AfterEach
    void close() {
        db.execute("SHUTDOWN");
    }

    @Test
    void recordsAuthenticatedOperatorAndIgnoresClientAuditor() {
        var request = request("operator");
        request.setAuditUser("someone-else");
        request.setRemark("审核通过");
        assertEquals(1, service.updateScmPurchaseOrder(request));
        assertEquals("operator", db.queryForObject("SELECT audit_user FROM erp_purchase_order WHERE id=1", String.class));
        assertEquals("operator", db.queryForObject("SELECT update_by FROM erp_purchase_order WHERE id=1", String.class));
        assertEquals("审核通过", db.queryForObject("SELECT remark FROM erp_purchase_order WHERE id=1", String.class));
        assertTrue(db.queryForObject("SELECT audit_time FROM erp_purchase_order WHERE id=1", Long.class) > 0);
        assertEquals(-1, service.updateScmPurchaseOrder(request("another-operator")));
        assertEquals("operator", db.queryForObject("SELECT audit_user FROM erp_purchase_order WHERE id=1", String.class));
    }

    @Test
    void needsNoClientAuditorButRejectsMissingAuthenticatedOperator() {
        assertEquals(-1, service.updateScmPurchaseOrder(request(null)));
        assertEquals(0, db.queryForObject("SELECT status FROM erp_purchase_order WHERE id=1", Integer.class));
        assertEquals(1, service.updateScmPurchaseOrder(request("admin")));
        assertEquals("admin", db.queryForObject("SELECT audit_user FROM erp_purchase_order WHERE id=1", String.class));
    }

    @Test
    void concurrentAuditsCannotOverwriteWinner() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return service.updateScmPurchaseOrder(request("alice")); });
            var b = pool.submit(() -> { start.await(); return service.updateScmPurchaseOrder(request("bob")); });
            start.countDown();
            int first = a.get(10, TimeUnit.SECONDS);
            int second = b.get(10, TimeUnit.SECONDS);
            assertTrue((first == 1 && second <= 0) || (second == 1 && first <= 0));
            assertEquals(first == 1 ? "alice" : "bob",
                    db.queryForObject("SELECT audit_user FROM erp_purchase_order WHERE id=1", String.class));
        } finally {
            pool.shutdownNow();
        }
    }

    private PurchaseOrderOptionRequest request(String actor) {
        var request = new PurchaseOrderOptionRequest();
        request.setId(1L);
        request.setOptionType("audit");
        request.setUpdateBy(actor); // Production Controller supplies this from the authenticated session.
        return request;
    }

    private String sqlType(Class<?> type) {
        if (type == LocalDateTime.class) return "timestamp";
        if (type == BigDecimal.class) return "decimal(12,2)";
        if (Number.class.isAssignableFrom(type)) return "bigint";
        return "varchar(255)";
    }
    @Test void cancellationReportsLostStateRaceInsteadOfPretendingSuccess() {
        var mapper=org.mockito.Mockito.mock(ErpPurchaseOrderMapper.class);
        var row=new ErpPurchaseOrder();row.setId(1L);row.setStatus(1);
        org.mockito.Mockito.when(mapper.selectById(1L)).thenReturn(row);
        var concurrent=new ErpPurchaseOrderServiceImpl(mapper,null,null,null,null,null);
        var command=request("operator");command.setOptionType("cancel");
        assertEquals(0,concurrent.updateScmPurchaseOrder(command));
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<ErpPurchaseOrder>> filter=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        org.mockito.Mockito.verify(mapper).update(org.mockito.ArgumentMatchers.any(ErpPurchaseOrder.class),filter.capture());
        assertTrue(filter.getValue().getSqlSegment().contains("status"));
        org.mockito.Mockito.verify(mapper,org.mockito.Mockito.never()).updateById(org.mockito.ArgumentMatchers.any(ErpPurchaseOrder.class));
    }
}
