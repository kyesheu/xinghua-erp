package cn.xinghuaerp.finance.service;

import cn.xinghuaerp.finance.entity.FmsExpense;
import cn.xinghuaerp.finance.entity.FmsExpenseItem;
import cn.xinghuaerp.finance.entity.FmsOrderSettlement;
import cn.xinghuaerp.finance.entity.FmsOrderSettlementItem;
import cn.xinghuaerp.finance.mapper.FmsExpenseItemMapper;
import cn.xinghuaerp.finance.mapper.FmsExpenseMapper;
import cn.xinghuaerp.finance.mapper.FmsOrderSettlementItemMapper;
import cn.xinghuaerp.finance.mapper.FmsOrderSettlementMapper;
import cn.xinghuaerp.finance.service.impl.FmsOrderSettlementServiceImpl;
import cn.xinghuaerp.inventory.facade.InventoryFacade;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.facade.OrderFacade;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 结算创建与取消的真并发验证。
 *
 * <p>与 {@link SettlementTransactionTest} 的单线程回滚用例互补：这里每个线程都用独立的
 * {@link DataSourceTransactionManager} 事务，并且全部读写都落到 H2（MySQL 模式）的真实表上，
 * 因此 {@code SELECT ... FOR UPDATE} 的行锁、条件更新的影响行数都是真实数据库行为，
 * 而不是 mock 的返回值。</p>
 *
 * <p>Mapper 桩只负责把 MyBatis-Plus 的 Wrapper 翻译成等价 SQL，不参与任何业务判断。</p>
 */
class SettlementConcurrencyTest {

    /** 沿父类链注入私有字段，与 SettlementTransactionTest 中的辅助方法保持一致。 */
    static void inject(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static final Pattern HOLES =
            Pattern.compile("#\\{ew\\.paramNameValuePairs\\.([A-Za-z0-9_]+)}");

    /** 把 Wrapper 生成的片段还原成可执行 SQL，并按占位符顺序收集参数。 */
    static String bind(String fragment, Map<String, Object> pairs, List<Object> values) {
        if (fragment == null) return "";
        Matcher matcher = HOLES.matcher(fragment);
        StringBuilder sql = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            values.add(pairs.get(matcher.group(1)));
            sql.append(fragment, cursor, matcher.start()).append('?');
            cursor = matcher.end();
        }
        sql.append(fragment.substring(cursor));
        return sql.toString();
    }

    /** 还原 Wrapper 的 where 子句（含 {@code .last(...)} 追加的 LIMIT / FOR UPDATE）。 */
    static String where(Wrapper<?> wrapper, List<Object> values) {
        var raw = (AbstractWrapper<?, ?, ?>) wrapper;
        String segment = bind(raw.getSqlSegment(), raw.getParamNameValuePairs(), values);
        return segment.isBlank() ? "" : " WHERE " + segment;
    }

    /**
     * 让 n 个线程尽量同时起跑，返回每个线程的结果标记。
     * 不依赖固定睡眠，只用 latch + 有限的 get 超时。
     */
    static List<String> race(int parties, java.util.function.IntFunction<String> body) throws Exception {
        var start = new CountDownLatch(1);
        var results = new ConcurrentLinkedQueue<String>();
        try (var pool = Executors.newFixedThreadPool(parties)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < parties; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    results.add(body.apply(index));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        }
        return List.copyOf(results);
    }

    /** H2 夹具：真实表结构 + 真实事务管理器，每个用例使用唯一的库名。 */
    static final class Fixture {
        final JdbcTemplate db;
        final DataSourceTransactionManager transactionManager;

        Fixture(String database) {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            this.db = new JdbcTemplate(ds);
            this.transactionManager = new DataSourceTransactionManager(ds);
            db.execute("CREATE TABLE o_order(id VARCHAR(32) PRIMARY KEY,order_num VARCHAR(64),shop_type INT,"
                    + "shop_id BIGINT,merchant_id BIGINT,amount DOUBLE,settlement_status INT,"
                    + "settlement_id BIGINT,settlement_time TIMESTAMP,create_time TIMESTAMP)");
            db.execute("CREATE TABLE fms_order_settlement(id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "settlement_no VARCHAR(64),merchant_id BIGINT,shop_id BIGINT,order_id BIGINT,order_no VARCHAR(64),"
                    + "sales_amount DECIMAL(18,4),purchase_cost DECIMAL(18,4),shipping_fee DECIMAL(18,4),"
                    + "platform_fee DECIMAL(18,4),marketing_fee DECIMAL(18,4),other_fee DECIMAL(18,4),"
                    + "total_cost DECIMAL(18,4),profit DECIMAL(18,4),profit_rate DECIMAL(18,4),version INT,"
                    + "remark VARCHAR(255),status INT,create_by VARCHAR(64),create_time TIMESTAMP,"
                    + "update_by VARCHAR(64),update_time TIMESTAMP)");
            db.execute("CREATE TABLE fms_order_settlement_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "settlement_id BIGINT,item_type INT,item_name VARCHAR(64),amount DECIMAL(18,4),"
                    + "related_id BIGINT,remark VARCHAR(255),create_time TIMESTAMP)");
            db.execute("CREATE TABLE fms_expense(id BIGINT AUTO_INCREMENT PRIMARY KEY,expense_no VARCHAR(64),"
                    + "expense_type INT,merchant_id BIGINT,shop_id BIGINT,amount DECIMAL(18,4),status INT,"
                    + "settlement_status INT,settlement_id BIGINT,settlement_time TIMESTAMP)");
            db.execute("CREATE TABLE fms_expense_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,expense_id BIGINT,"
                    + "order_id BIGINT,order_no VARCHAR(64),amount DECIMAL(18,4),settlement_status INT,"
                    + "settlement_id BIGINT,settlement_time TIMESTAMP)");
        }

        void shutdown() {
            db.execute("SHUTDOWN");
        }

        void seedOrder(String orderId, double amount) {
            db.update("INSERT INTO o_order(id,order_num,shop_id,merchant_id,amount,settlement_status,create_time)"
                    + " VALUES(?,?,?,?,?,?,?)", orderId, "NO" + orderId, 10L, 7L, amount, 0,
                    java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)));
        }

        void seedApprovedExpense(long expenseId, long expenseItemId, long orderId, BigDecimal amount) {
            db.update("INSERT INTO fms_expense(id,expense_no,expense_type,amount,status,settlement_status)"
                            + " VALUES(?,?,?,?,?,?)",
                    expenseId, "EXP" + expenseId, 10, amount, 5, 0);
            db.update("INSERT INTO fms_expense_item(id,expense_id,order_id,amount,settlement_status)"
                            + " VALUES(?,?,?,?,?)",
                    expenseItemId, expenseId, orderId, amount, 0);
        }

        int count(String sql, Object... args) {
            Integer value = db.queryForObject(sql, Integer.class, args);
            return value == null ? 0 : value;
        }

        /** 组装被测服务：所有依赖都是把 Wrapper 翻译成 SQL 的真实 JDBC 桩。 */
        FmsOrderSettlementServiceImpl newService() throws Exception {
            var service = new FmsOrderSettlementServiceImpl();
            inject(service, "baseMapper", settlementMapper());
            inject(service, "settlementItemMapper", settlementItemMapper());
            inject(service, "expenseItemMapper", expenseItemMapper());
            inject(service, "expenseMapper", expenseMapper());
            inject(service, "orderMapper", orderFacade());
            var inventory = mock(InventoryFacade.class);
            when(inventory.listStockingBatches(any())).thenReturn(List.of());
            when(inventory.listOOrderStocking(any())).thenReturn(List.of());
            inject(service, "orderStockingMapper", inventory);
            inject(service, "itemBatchMapper", inventory);
            inject(service, "transactionManager", transactionManager);
            return service;
        }

        OrderFacade orderFacade() {
            var facade = mock(OrderFacade.class);
            when(facade.getOOrderOne(any())).thenAnswer(call -> {
                var raw = (AbstractWrapper<?, ?, ?>) call.getArgument(0, Wrapper.class);
                // 结算读取订单必须带行锁，否则并发下两个线程都会判定“未结算”。
                assertTrue(raw.getSqlSegment().toUpperCase().contains("FOR UPDATE"),
                        "结算流程读取订单必须加 SELECT ... FOR UPDATE");
                List<Object> values = new ArrayList<>();
                String sql = "SELECT id,order_num,shop_id,merchant_id,amount,settlement_status,"
                        + "settlement_id,settlement_time FROM o_order" + where(raw, values);
                List<OOrder> rows = db.query(sql, ORDER_MAPPER, values.toArray());
                return rows.isEmpty() ? null : rows.get(0);
            });
            when(facade.getOOrderById(any())).thenAnswer(call -> {
                Object orderId = call.getArgument(0);
                List<OOrder> rows = db.query("SELECT id,order_num,shop_id,merchant_id,amount,"
                        + "settlement_status,settlement_id,settlement_time FROM o_order WHERE id=?",
                        ORDER_MAPPER, String.valueOf(orderId));
                return rows.isEmpty() ? null : rows.get(0);
            });
            when(facade.updateOOrder(any())).thenAnswer(call -> {
                OOrder order = call.getArgument(0, OOrder.class);
                return db.update("UPDATE o_order SET settlement_status=?,settlement_id=?,settlement_time=?"
                                + " WHERE id=?",
                        order.getSettlementStatus(), order.getSettlementId(),
                        order.getSettlementTime() == null ? null
                                : java.sql.Timestamp.valueOf(order.getSettlementTime()),
                        order.getId());
            });
            when(facade.clearOrderSettlement(any(), any())).thenAnswer(call -> {
                Object orderId = call.getArgument(0);
                Object settlementId = call.getArgument(1);
                return db.update("UPDATE o_order SET settlement_status=0,settlement_id=NULL,"
                                + "settlement_time=NULL WHERE id=? AND settlement_id=?",
                        String.valueOf(orderId), settlementId);
            });
            return facade;
        }

        FmsOrderSettlementMapper settlementMapper() {
            var mapper = mock(FmsOrderSettlementMapper.class);
            when(mapper.insert(any(FmsOrderSettlement.class))).thenAnswer(call -> {
                FmsOrderSettlement row = call.getArgument(0, FmsOrderSettlement.class);
                var keys = new GeneratedKeyHolder();
                db.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO fms_order_settlement(settlement_no,merchant_id,shop_id,order_id,"
                                    + "order_no,sales_amount,purchase_cost,shipping_fee,platform_fee,"
                                    + "marketing_fee,other_fee,total_cost,profit,profit_rate,version,status,"
                                    + "create_by,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            new String[]{"id"});
                    ps.setString(1, row.getSettlementNo());
                    ps.setObject(2, row.getMerchantId());
                    ps.setObject(3, row.getShopId());
                    ps.setObject(4, row.getOrderId());
                    ps.setString(5, row.getOrderNo());
                    ps.setBigDecimal(6, row.getSalesAmount());
                    ps.setBigDecimal(7, row.getPurchaseCost());
                    ps.setBigDecimal(8, row.getShippingFee());
                    ps.setBigDecimal(9, row.getPlatformFee());
                    ps.setBigDecimal(10, row.getMarketingFee());
                    ps.setBigDecimal(11, row.getOtherFee());
                    ps.setBigDecimal(12, row.getTotalCost());
                    ps.setBigDecimal(13, row.getProfit());
                    ps.setBigDecimal(14, row.getProfitRate());
                    ps.setObject(15, row.getVersion());
                    ps.setObject(16, row.getStatus());
                    ps.setString(17, row.getCreateBy());
                    ps.setTimestamp(18, row.getCreateTime() == null ? null
                            : java.sql.Timestamp.valueOf(row.getCreateTime()));
                    return ps;
                }, keys);
                row.setId(keys.getKey().longValue());
                return 1;
            });
            when(mapper.selectById(any())).thenAnswer(call -> {
                Object settlementId = call.getArgument(0);
                List<FmsOrderSettlement> rows = db.query("SELECT id,order_id FROM fms_order_settlement"
                        + " WHERE id=?", SETTLEMENT_MAPPER, settlementId);
                return rows.isEmpty() ? null : rows.get(0);
            });
            when(mapper.selectOne(any())).thenAnswer(call -> {
                List<Object> values = new ArrayList<>();
                String sql = "SELECT id,order_id FROM fms_order_settlement"
                        + where(call.getArgument(0, Wrapper.class), values);
                List<FmsOrderSettlement> rows = db.query(sql, SETTLEMENT_MAPPER, values.toArray());
                return rows.isEmpty() ? null : rows.get(0);
            });
            when(mapper.deleteById(anyLong())).thenAnswer(call -> {
                Object settlementId = call.getArgument(0);
                return db.update("DELETE FROM fms_order_settlement WHERE id=?", settlementId);
            });
            return mapper;
        }

        FmsOrderSettlementItemMapper settlementItemMapper() {
            var mapper = mock(FmsOrderSettlementItemMapper.class);
            when(mapper.insert(any(FmsOrderSettlementItem.class))).thenAnswer(call -> {
                FmsOrderSettlementItem item = call.getArgument(0, FmsOrderSettlementItem.class);
                return db.update("INSERT INTO fms_order_settlement_item(settlement_id,item_type,item_name,"
                                + "amount,create_time) VALUES(?,?,?,?,?)",
                        item.getSettlementId(), item.getItemType(), item.getItemName(), item.getAmount(),
                        item.getCreateTime() == null ? null
                                : java.sql.Timestamp.valueOf(item.getCreateTime()));
            });
            when(mapper.delete(any())).thenAnswer(call -> {
                List<Object> values = new ArrayList<>();
                String sql = "DELETE FROM fms_order_settlement_item"
                        + where(call.getArgument(0, Wrapper.class), values);
                return db.update(sql, values.toArray());
            });
            when(mapper.selectList(any())).thenAnswer(call -> {
                List<Object> values = new ArrayList<>();
                String sql = "SELECT id,settlement_id,item_type,item_name,amount FROM fms_order_settlement_item"
                        + where(call.getArgument(0, Wrapper.class), values);
                return db.query(sql, (rs, n) -> {
                    var item = new FmsOrderSettlementItem();
                    item.setId(rs.getLong("id"));
                    item.setSettlementId(readLong(rs, "settlement_id"));
                    item.setItemType(readInteger(rs, "item_type"));
                    item.setItemName(rs.getString("item_name"));
                    item.setAmount(rs.getBigDecimal("amount"));
                    return item;
                }, values.toArray());
            });
            return mapper;
        }

        FmsExpenseItemMapper expenseItemMapper() {
            var mapper = mock(FmsExpenseItemMapper.class);
            when(mapper.selectList(any())).thenAnswer(call -> {
                List<Object> values = new ArrayList<>();
                String sql = "SELECT id,expense_id,order_id,amount,settlement_status,settlement_id"
                        + " FROM fms_expense_item" + where(call.getArgument(0, Wrapper.class), values);
                return db.query(sql, (rs, n) -> {
                    var item = new FmsExpenseItem();
                    item.setId(rs.getLong("id"));
                    item.setExpenseId(readLong(rs, "expense_id"));
                    item.setOrderId(readLong(rs, "order_id"));
                    item.setAmount(rs.getBigDecimal("amount"));
                    item.setSettlementStatus(readInteger(rs, "settlement_status"));
                    item.setSettlementId(readLong(rs, "settlement_id"));
                    return item;
                }, values.toArray());
            });
            when(mapper.selectCount(any())).thenAnswer(call -> {
                List<Object> values = new ArrayList<>();
                String sql = "SELECT COUNT(*) FROM fms_expense_item"
                        + where(call.getArgument(0, Wrapper.class), values);
                return Long.valueOf(db.queryForObject(sql, Integer.class, values.toArray()));
            });
            when(mapper.updateById(any(FmsExpenseItem.class))).thenAnswer(call -> {
                FmsExpenseItem item = call.getArgument(0, FmsExpenseItem.class);
                return db.update("UPDATE fms_expense_item SET settlement_status=?,settlement_id=?,"
                                + "settlement_time=? WHERE id=?",
                        item.getSettlementStatus(), item.getSettlementId(),
                        item.getSettlementTime() == null ? null
                                : java.sql.Timestamp.valueOf(item.getSettlementTime()), item.getId());
            });
            when(mapper.update(isNull(), any())).thenAnswer(call -> {
                var raw = (AbstractWrapper<?, ?, ?>) call.getArgument(1, Wrapper.class);
                // set 与 where 共用同一份 paramNameValuePairs，参数顺序按占位符出现顺序还原。
                List<Object> values = new ArrayList<>();
                String setSql = bind(raw.getSqlSet(), raw.getParamNameValuePairs(), values);
                String whereSql = bind(raw.getSqlSegment(), raw.getParamNameValuePairs(), values);
                return db.update("UPDATE fms_expense_item SET " + setSql
                        + (whereSql.isBlank() ? "" : " WHERE " + whereSql), values.toArray());
            });
            return mapper;
        }

        FmsExpenseMapper expenseMapper() {
            var mapper = mock(FmsExpenseMapper.class);
            when(mapper.selectList(any())).thenAnswer(call -> {
                List<Object> values = new ArrayList<>();
                String sql = "SELECT id,expense_type,status,settlement_status,settlement_id FROM fms_expense"
                        + where(call.getArgument(0, Wrapper.class), values);
                return db.query(sql, EXPENSE_MAPPER, values.toArray());
            });
            when(mapper.selectById(any())).thenAnswer(call -> {
                Object expenseId = call.getArgument(0);
                List<FmsExpense> rows = db.query("SELECT id,expense_type,status,settlement_status,"
                        + "settlement_id FROM fms_expense WHERE id=?", EXPENSE_MAPPER, expenseId);
                return rows.isEmpty() ? null : rows.get(0);
            });
            when(mapper.updateById(any(FmsExpense.class))).thenAnswer(call -> {
                FmsExpense expense = call.getArgument(0, FmsExpense.class);
                return db.update("UPDATE fms_expense SET settlement_status=?,settlement_id=?,settlement_time=?"
                                + " WHERE id=?",
                        expense.getSettlementStatus(), expense.getSettlementId(),
                        expense.getSettlementTime() == null ? null
                                : java.sql.Timestamp.valueOf(expense.getSettlementTime()), expense.getId());
            });
            when(mapper.update(isNull(), any())).thenAnswer(call -> {
                var raw = (AbstractWrapper<?, ?, ?>) call.getArgument(1, Wrapper.class);
                List<Object> values = new ArrayList<>();
                String setSql = bind(raw.getSqlSet(), raw.getParamNameValuePairs(), values);
                String whereSql = bind(raw.getSqlSegment(), raw.getParamNameValuePairs(), values);
                return db.update("UPDATE fms_expense SET " + setSql
                        + (whereSql.isBlank() ? "" : " WHERE " + whereSql), values.toArray());
            });
            return mapper;
        }
    }

    private static final RowMapper<OOrder> ORDER_MAPPER = (rs, n) -> {
        var order = new OOrder();
        order.setId(rs.getString("id"));
        order.setOrderNum(rs.getString("order_num"));
        order.setShopId(readLong(rs, "shop_id"));
        order.setMerchantId(readLong(rs, "merchant_id"));
        double amount = rs.getDouble("amount");
        order.setAmount(rs.wasNull() ? null : amount);
        order.setSettlementStatus(readInteger(rs, "settlement_status"));
        order.setSettlementId(readLong(rs, "settlement_id"));
        order.setSettlementTime(readTime(rs, "settlement_time"));
        return order;
    };

    private static final RowMapper<FmsOrderSettlement> SETTLEMENT_MAPPER = (rs, n) -> {
        var row = new FmsOrderSettlement();
        row.setId(rs.getLong("id"));
        row.setOrderId(readLong(rs, "order_id"));
        return row;
    };

    private static final RowMapper<FmsExpense> EXPENSE_MAPPER = (rs, n) -> {
        var expense = new FmsExpense();
        expense.setId(rs.getLong("id"));
        expense.setExpenseType(readInteger(rs, "expense_type"));
        expense.setStatus(readInteger(rs, "status"));
        expense.setSettlementStatus(readInteger(rs, "settlement_status"));
        expense.setSettlementId(readLong(rs, "settlement_id"));
        return expense;
    };

    private static Long readLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer readInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime readTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    // ---------------------------------------------------------------- 用例

    @Test
    void concurrentManualSettlementCreatesExactlyOneSettlementForSameOrder() throws Exception {
        var fixture = new Fixture("settlement_concurrency_create");
        fixture.seedOrder("1", 1000.00d);
        fixture.seedApprovedExpense(5L, 7L, 1L, new BigDecimal("30.00"));
        var first = fixture.newService();
        var second = fixture.newService();

        List<String> outcomes = race(2, index -> {
            FmsOrderSettlementServiceImpl service = index == 0 ? first : second;
            try {
                var result = service.manualSettlement("1", null, "tester" + index);
                return Boolean.TRUE.equals(result.get("success")) ? "success" : "rejected:" + result.get("message");
            } catch (RuntimeException conflict) {
                // 数据库层面拒绝（行锁冲突 / 唯一性冲突）也是明确的失败。
                return "conflict:" + conflict.getClass().getSimpleName();
            }
        });

        assertEquals(1, outcomes.stream().filter("success"::equals).count(),
                "同一订单并发结算必须恰好成功一次，实际：" + outcomes);
        assertTrue(outcomes.stream().filter(outcome -> !"success".equals(outcome)).count() == 1,
                "另一个线程必须得到明确的失败/冲突结果，实际：" + outcomes);
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM fms_order_settlement WHERE order_id=1"),
                "同一订单不允许产生第二条结算记录");
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM fms_order_settlement"));
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM fms_order_settlement_item"),
                "结算明细只应写入一次");
        Integer settlementStatus = fixture.db.queryForObject(
                "SELECT settlement_status FROM o_order WHERE id='1'", Integer.class);
        Long settlementId = fixture.db.queryForObject(
                "SELECT settlement_id FROM o_order WHERE id='1'", Long.class);
        assertNotNull(settlementId, "订单必须关联到唯一结算单");
        assertEquals(1, settlementStatus.intValue(), "订单结算状态只应被写一次");
        assertNotNull(fixture.db.queryForObject("SELECT settlement_time FROM o_order WHERE id='1'",
                java.sql.Timestamp.class));
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM fms_expense_item WHERE order_id=1"
                + " AND settlement_status=1 AND settlement_id=?", settlementId));
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM fms_expense WHERE settlement_status=1"
                + " AND settlement_id=?", settlementId));
        fixture.shutdown();
    }

    @Test
    void concurrentCancelClearsOrderAndExpenseLinksExactlyOnce() throws Exception {
        var fixture = new Fixture("settlement_concurrency_cancel");
        fixture.seedOrder("1", 1000.00d);
        fixture.seedApprovedExpense(5L, 7L, 1L, new BigDecimal("30.00"));
        var created = fixture.newService().manualSettlement("1", null, "tester");
        assertEquals(Boolean.TRUE, created.get("success"), "前置条件：单线程结算必须成功");
        long settlementId = (Long) created.get("settlementId");
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM fms_order_settlement_item"));

        var first = fixture.newService();
        var second = fixture.newService();
        List<String> outcomes = race(2, index -> {
            FmsOrderSettlementServiceImpl service = index == 0 ? first : second;
            // 生产环境由 @Transactional 代理开启事务，这里用等价的 TransactionTemplate 驱动。
            var transaction = new TransactionTemplate(fixture.transactionManager);
            try {
                Boolean cancelled = transaction.execute(status ->
                        Boolean.valueOf(service.cancelSettlement(settlementId, "tester" + index)));
                return Boolean.TRUE.equals(cancelled) ? "cancelled" : "rejected";
            } catch (RuntimeException conflict) {
                return "conflict:" + conflict.getClass().getSimpleName();
            }
        });

        assertEquals(1, outcomes.stream().filter("cancelled"::equals).count(),
                "同一结算单并发取消必须恰好成功一次，实际：" + outcomes);
        assertEquals(1, outcomes.stream().filter(outcome -> !"cancelled".equals(outcome)).count(),
                "另一个线程必须得到明确的失败/冲突结果，实际：" + outcomes);
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM fms_order_settlement"), "结算单必须被取消");
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM fms_order_settlement_item"),
                "结算明细只应被清理一次");
        Integer settlementStatus = fixture.db.queryForObject(
                "SELECT settlement_status FROM o_order WHERE id='1'", Integer.class);
        assertEquals(0, settlementStatus.intValue(), "订单结算状态必须回到未结算");
        assertNull(fixture.db.queryForObject("SELECT settlement_id FROM o_order WHERE id='1'", Long.class));
        assertNull(fixture.db.queryForObject("SELECT settlement_time FROM o_order WHERE id='1'",
                java.sql.Timestamp.class));
        assertNull(fixture.db.queryForObject("SELECT settlement_id FROM fms_expense_item WHERE id=7",
                Long.class));
        assertEquals(0, fixture.count("SELECT settlement_status FROM fms_expense_item WHERE id=7"));
        assertNull(fixture.db.queryForObject("SELECT settlement_id FROM fms_expense WHERE id=5", Long.class));
        assertEquals(0, fixture.count("SELECT settlement_status FROM fms_expense WHERE id=5"));
        fixture.shutdown();
    }

    @Test
    void repeatedCancelAfterSettlementIsRejectedWithoutRepaintingClearedFields() throws Exception {
        var fixture = new Fixture("settlement_concurrency_repeat_cancel");
        fixture.seedOrder("1", 1000.00d);
        fixture.seedApprovedExpense(5L, 7L, 1L, new BigDecimal("30.00"));
        var created = fixture.newService().manualSettlement("1", null, "tester");
        assertEquals(Boolean.TRUE, created.get("success"), "前置条件：单线程结算必须成功");
        long settlementId = (Long) created.get("settlementId");

        var service = fixture.newService();
        var transaction = new TransactionTemplate(fixture.transactionManager);
        Boolean firstCancel = transaction.execute(status ->
                Boolean.valueOf(service.cancelSettlement(settlementId, "tester")));
        assertTrue(Boolean.TRUE.equals(firstCancel), "第一次取消必须成功");

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("order", fixture.db.queryForMap("SELECT settlement_status,settlement_id,settlement_time"
                + " FROM o_order WHERE id='1'"));
        before.put("expense", fixture.db.queryForMap("SELECT settlement_status,settlement_id,settlement_time"
                + " FROM fms_expense WHERE id=5"));
        before.put("item", fixture.db.queryForMap("SELECT settlement_status,settlement_id,settlement_time"
                + " FROM fms_expense_item WHERE id=7"));

        Boolean secondCancel = transaction.execute(status ->
                Boolean.valueOf(service.cancelSettlement(settlementId, "tester2")));

        assertEquals(Boolean.FALSE, secondCancel, "结算单已不存在，第二次取消必须是明确的失败而不是静默成功");
        assertEquals(before.get("order"),
                fixture.db.queryForMap("SELECT settlement_status,settlement_id,settlement_time"
                        + " FROM o_order WHERE id='1'"), "第二次取消不能重写订单已清空的结算字段");
        assertEquals(before.get("expense"),
                fixture.db.queryForMap("SELECT settlement_status,settlement_id,settlement_time"
                        + " FROM fms_expense WHERE id=5"), "第二次取消不能重写费用单字段");
        assertEquals(before.get("item"),
                fixture.db.queryForMap("SELECT settlement_status,settlement_id,settlement_time"
                        + " FROM fms_expense_item WHERE id=7"), "第二次取消不能重写费用明细字段");
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM fms_order_settlement"));
        fixture.shutdown();
    }
}
