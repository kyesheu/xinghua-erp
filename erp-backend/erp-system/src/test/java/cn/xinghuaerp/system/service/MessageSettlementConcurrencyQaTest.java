package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.mapper.SysMessageMapper;
import cn.xinghuaerp.system.service.impl.SysMessageServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QA 独立复核：通知结果结算（{@code finishNotify}）与已读标记（{@code markRead}）的并发语义。
 *
 * <p>与开发自测的区别：这里不依赖任何 mock 返回值来「证明」并发正确，而是用两个真实线程
 * + {@link CountDownLatch} 反复制造交错，最后只断言<b>数据库里留下的那一行</b>。</p>
 *
 * <p>原缺陷（HEAD 756a209）：结算走 {@code save(msg)} → {@code updateById} 整实体回写，
 * 推送阻塞期间用户写入的 {@code is_read=1} 会被读取时的旧快照 {@code is_read=0} 覆盖回未读。</p>
 */
class MessageSettlementConcurrencyQaTest {

    /** 交错轮数：单轮跑不出问题，200 轮足以让交错顺序覆盖到关键窗口。 */
    private static final int ROUNDS = 200;

    /** 已读操作与投递结算的并发，属于同一行的争用；给足锁等待，避免把「锁超时」误判成缺陷。 */
    private JdbcTemplate db;
    private SysMessageMapper mapper;
    private SysMessageServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:settle_qa_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=LEVEL;LOCK_TIMEOUT=10000");
        db = new JdbcTemplate(dataSource);
        db.execute("CREATE TABLE sys_message(id BIGINT AUTO_INCREMENT PRIMARY KEY,dedup_key VARCHAR(128),"
                + "type VARCHAR(32),level VARCHAR(16),title VARCHAR(128),content VARCHAR(512),link VARCHAR(256),"
                + "source VARCHAR(32),is_read INT,need_notify INT,notify_status INT,notify_time TIMESTAMP,"
                + "notify_token VARCHAR(36),created_time TIMESTAMP,read_time TIMESTAMP)");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(SysMessageMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        mapper = session.getMapper(SysMessageMapper.class);
        service = new SysMessageServiceImpl(mapper);
    }

    @AfterEach
    void tearDown() {
        db.execute("SHUTDOWN");
    }

    /**
     * 真实并发：线程 A 抢占后结算，线程 B 同时标记已读，起跑线对齐，跑 200 轮。
     *
     * <p>断言与交错顺序无关的两个不变量：{@code is_read} 必须是 1（已读不丢）、
     * {@code notify_status} 必须停在结算值 1（不能被已读操作/旧快照改回 0）。</p>
     */
    @Test
    @DisplayName("200 轮真实并发：已读与结算互相覆盖 0 次")
    void concurrentMarkReadAndSettlementNeverLoseEachOther() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                final long id = insertPendingMessage();
                final int currentRound = round;
                CountDownLatch startLine = new CountDownLatch(1);
                CountDownLatch bothDone = new CountDownLatch(2);

                Future<?> delivery = pool.submit(() -> {
                    try {
                        startLine.await();
                        String token = service.claimNotify(id, 0);
                        assertNotNull(token, "第" + currentRound + "轮：抢占必须成功");
                        assertTrue(service.finishNotify(id, 1, token), "第" + currentRound + "轮：围栏匹配时结算必须生效");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        bothDone.countDown();
                    }
                });
                Future<?> reading = pool.submit(() -> {
                    try {
                        startLine.await();
                        service.markRead(id);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        bothDone.countDown();
                    }
                });

                startLine.countDown();
                assertTrue(bothDone.await(30, TimeUnit.SECONDS), "第" + round + "轮：两个线程必须都能结束");
                delivery.get();
                reading.get();

                assertEquals(1, intColumn("is_read", id), "第" + round + "轮：已读标记被旧快照覆盖丢失");
                assertEquals(1, intColumn("notify_status", id), "第" + round + "轮：投递结算结果被覆盖");
                assertNotNull(timestampColumn("read_time", id), "第" + round + "轮：阅读时间丢失");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 确定性复现原始 lost update 窗口：先抢占 → 推送期间用户点已读 → 再结算。
     *
     * <p>这就是缺陷报告里的场景，用 latch 把顺序钉死，不靠随机交错。</p>
     */
    @Test
    @DisplayName("投递阻塞期间标记已读：结算后已读必须保留（原始 lost update 窗口，200 轮）")
    void markReadDuringDeliveryIsNeverOverwritten() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                final long id = insertPendingMessage();
                final int currentRound = round;
                CountDownLatch claimed = new CountDownLatch(1);
                CountDownLatch readFinished = new CountDownLatch(1);
                CountDownLatch settled = new CountDownLatch(1);

                Future<?> delivery = pool.submit(() -> {
                    try {
                        String token = service.claimNotify(id, 0);
                        assertNotNull(token, "第" + currentRound + "轮：抢占必须成功");
                        claimed.countDown();
                        assertTrue(readFinished.await(30, TimeUnit.SECONDS), "等待已读完成超时");
                        assertTrue(service.finishNotify(id, 1, token), "第" + currentRound + "轮：结算必须生效");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        settled.countDown();
                    }
                });
                Future<?> reading = pool.submit(() -> {
                    try {
                        assertTrue(claimed.await(30, TimeUnit.SECONDS), "等待抢占完成超时");
                        service.markRead(id);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        readFinished.countDown();
                    }
                });

                assertTrue(settled.await(60, TimeUnit.SECONDS));
                delivery.get();
                reading.get();

                assertEquals(1, intColumn("is_read", id), "第" + round + "轮：推送期间的已读被结算覆盖（lost update 未修好）");
                assertEquals(1, intColumn("notify_status", id), "第" + round + "轮：结算结果必须落库");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("结算的 SQL 只 set 投递三列，既不 set 也不 where is_read，且围栏带租约令牌")
    void settlementSqlTouchesOnlyNotifyColumns() throws Exception {
        initTableInfoOnce();
        SysMessageMapper mockMapper = mock(SysMessageMapper.class);
        when(mockMapper.update(isNull(), any())).thenReturn(1);
        SysMessageServiceImpl mockService = new SysMessageServiceImpl(mockMapper);

        assertTrue(mockService.finishNotify(7L, 2, "lease-token-qa"), "影响行数 1 时必须返回 true");

        UpdateWrapper<SysMessage> wrapper = capturedUpdate(mockMapper);
        String set = wrapper.getSqlSet();
        assertTrue(set.contains("notify_status"), "必须 set notify_status，实际：" + set);
        assertTrue(set.contains("notify_time"), "必须 set notify_time，实际：" + set);
        assertTrue(set.contains("notify_token"), "结算必须清空租约令牌，实际：" + set);
        assertEquals(2, countChar(set, ','), "set 子句只允许投递相关三列，实际：" + set);
        for (String forbidden : new String[]{"is_read", "read_time", "title", "content", "need_notify", "dedup_key"}) {
            assertFalse(set.contains(forbidden), "结算不得写 " + forbidden + "，实际：" + set);
        }
        String where = wrapper.getSqlSegment();
        assertTrue(where.contains("notify_status"), "围栏条件必须包含 notify_status，实际：" + where);
        assertTrue(where.contains("notify_token"), "围栏必须按租约令牌匹配持有者，实际：" + where);
        assertFalse(where.contains("is_read"), "围栏不得涉及 is_read，实际：" + where);
    }

    @Test
    @DisplayName("已结算的消息不会被再次结算（围栏在状态已变更后必须挡住）")
    void secondSettlementIsFencedOut() {
        long id = insertPendingMessage();
        String token = service.claimNotify(id, 0);
        assertNotNull(token);
        assertTrue(service.finishNotify(id, 1, token));
        Timestamp settledAt = timestampColumn("notify_time", id);

        assertFalse(service.finishNotify(id, 2, token), "状态已是 1（已推送），再次结算必须返回 false");
        assertEquals(1, intColumn("notify_status", id), "不得把已结算的状态改成别的值");
        assertEquals(settledAt, timestampColumn("notify_time", id), "结算被挡时不得改 notify_time");
    }

    /**
     * 租约归属：10 分钟窗口后被 B 接管，A 那份早已过时的结果不得覆盖 B 的真实结果。
     *
     * <p>这是引入租约令牌要解决的问题。此前围栏只有 {@code notify_status=3}，接管时状态仍是 3，
     * A 稍后回来照样能结算成功、把 B 的结果挡掉（先到者赢）。现在接管换发新令牌，A 的结算影响行数为 0。</p>
     */
    @Test
    @DisplayName("租约被接管后由持有者说了算：过期持有者的结算被拒，接管者结果保留")
    void takeoverIsSettledByLeaseOwnerOnly() {
        long id = insertPendingMessage();
        String tokenA = service.claimNotify(id, 0);
        String tokenB = service.claimNotify(id, 3);
        assertNotNull(tokenA, "实例 A 抢到租约");
        assertNotNull(tokenB, "实例 B 在 10 分钟窗口后接管");
        assertNotEquals(tokenA, tokenB, "接管必须换发新令牌");

        assertFalse(service.finishNotify(id, 2, tokenA), "A 已超时且令牌被换掉，结算必须被拒绝");
        assertEquals(3, intColumn("notify_status", id), "A 的失败不得改动 B 正在发送的状态");

        assertTrue(service.finishNotify(id, 1, tokenB), "B 是当前持有者，结算必须生效");
        assertEquals(1, intColumn("notify_status", id), "最终留下的是接管者的真实结果，不是 A 的过期结果");
        assertNull(stringColumn("notify_token", id), "结算后令牌必须清空");
    }

    private UpdateWrapper<SysMessage> capturedUpdate(SysMessageMapper mockMapper) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SysMessage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mockMapper).update(isNull(), captor.capture());
        return (UpdateWrapper<SysMessage>) captor.getValue();
    }

    private static int countChar(String text, char target) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) count++;
        }
        return count;
    }

    private static void initTableInfoOnce() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "settle-qa"), SysMessage.class);
    }

    private long insertPendingMessage() {
        db.update("INSERT INTO sys_message(type,level,title,content,source,is_read,need_notify,notify_status,"
                        + "created_time) VALUES(?,?,?,?,?,?,?,?,?)",
                "stock_low", "high", "库存不足: 电饭煲", "SKU 只剩 1 件", "system", 0, 1, 0,
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(30)));
        Long id = db.queryForObject("SELECT MAX(id) FROM sys_message", Long.class);
        assertNotNull(id, "消息必须落库成功");
        return id;
    }

    private int intColumn(String column, long id) {
        Integer value = db.queryForObject("SELECT " + column + " FROM sys_message WHERE id=?", Integer.class, id);
        assertNotNull(value, column + " 不应为 null");
        return value;
    }

    private Timestamp timestampColumn(String column, long id) {
        return db.queryForObject("SELECT " + column + " FROM sys_message WHERE id=?", Timestamp.class, id);
    }

    private String stringColumn(String column, long id) {
        return db.queryForObject("SELECT " + column + " FROM sys_message WHERE id=?", String.class, id);
    }
}
