package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.mapper.SysMessageMapper;
import cn.xinghuaerp.system.service.impl.SysMessageServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知结果结算（{@code finishNotify}）的并发语义验证。
 *
 * <p>用真实 MyBatis-Plus 映射（{@code updateById} 走的是真实的 NOT_NULL 策略）跑在隔离的 H2 上，
 * 因此这里断言的不是 mock 的返回值，而是「数据库里最终留下了什么」：</p>
 * <ul>
 *   <li>结算只写 {@code notify_status/notify_time} 并清空 {@code notify_token}，不会把投递期间
 *       用户写入的 {@code is_read/read_time/title/content} 覆盖回读取时的旧快照（原缺陷是 lost update）；</li>
 *   <li>结算围栏是 {@code notify_status=发送中 AND notify_token=本次令牌}：租约被接管后令牌已被换掉，
 *       过期持有者的结算返回 false 且一列都不改，不会覆盖接管者的真实结果。</li>
 * </ul>
 */
class MessageFinishNotifyTest {

    private JdbcTemplate db;
    private SysMessageMapper mapper;
    private SysMessageServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:finish_notify_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=LEVEL");
        db = new JdbcTemplate(dataSource);
        db.execute("CREATE TABLE sys_message(id BIGINT AUTO_INCREMENT PRIMARY KEY,dedup_key VARCHAR(128),"
                + "type VARCHAR(32),level VARCHAR(16),title VARCHAR(128),content VARCHAR(512),link VARCHAR(256),"
                + "source VARCHAR(32),is_read INT,need_notify INT,notify_status INT,notify_time TIMESTAMP,"
                + "notify_token VARCHAR(36),created_time TIMESTAMP,read_time TIMESTAMP)");

        var configuration = new MybatisConfiguration();
        configuration.addMapper(SysMessageMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        mapper = session.getMapper(SysMessageMapper.class);
        service = new SysMessageServiceImpl(mapper);
    }

    @AfterEach
    void tearDown() {
        db.execute("SHUTDOWN");
    }

    @Test
    @DisplayName("结算投递结果时不覆盖投递期间并发的已读/标题修改")
    void finishNotifyOnlySettlesDeliveryAndKeepsConcurrentModifications() {
        long id = insertPendingMessage();
        String token = service.claimNotify(id, 0);
        assertNotNull(token, "待推送消息应当抢占成功并拿到租约令牌");

        // 推送阻塞期间，用户点了「标记已读」并把标题改掉了
        db.update("UPDATE sys_message SET is_read=1, read_time=?, title=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().withNano(0)), "库存不足: 电饭煲（用户已读）", id);

        assertTrue(service.finishNotify(id, 1, token), "围栏匹配时结算必须生效");

        assertEquals(1, intColumn("is_read", id), "并发写入的已读标记不能被旧快照覆盖");
        assertNotNull(timestampColumn("read_time", id), "并发写入的阅读时间不能被清空");
        assertEquals("库存不足: 电饭煲（用户已读）", stringColumn("title", id), "标题不能被旧快照覆盖");
        assertEquals("SKU 只剩 1 件", stringColumn("content", id));
        assertEquals(1, intColumn("notify_status", id), "投递结果必须落库");
        assertNotNull(timestampColumn("notify_time", id));
        assertNull(stringColumn("notify_token", id), "结算后必须清空租约令牌，避免残留旧令牌");
    }

    @Test
    @DisplayName("租约被接管后，过期持有者的结算被拒绝，接管者的真实结果留到最后")
    void staleHolderCannotOverwriteTakeoverResult() {
        long id = insertPendingMessage();
        String tokenA = service.claimNotify(id, 0);
        assertNotNull(tokenA, "实例 A 应当抢占成功");

        // A 的投递卡住超过 10 分钟补偿窗口，实例 B 从「发送中」接管。
        // 注意此时 notify_status 一直是 3：只看状态围栏的话，A 稍后回来照样能结算。
        String tokenB = service.claimNotify(id, 3);
        assertNotNull(tokenB, "实例 B 应当能接管超时的租约");
        assertNotEquals(tokenA, tokenB, "接管必须换发新令牌，否则无法区分持有者");
        Timestamp afterTakeover = timestampColumn("notify_time", id);

        // A 的推送此时才返回（它那份结果早已过时），不能覆盖 B
        assertFalse(service.finishNotify(id, 1, tokenA), "过期令牌的结算必须被拒绝");
        assertEquals(3, intColumn("notify_status", id), "B 仍在发送中，状态不得被 A 改写");
        assertEquals(tokenB, stringColumn("notify_token", id), "令牌必须还是 B 的");
        assertEquals(afterTakeover, timestampColumn("notify_time", id), "被拒绝的结算不得改任何列");

        // B 用真实结果结算才生效：最终留下的是接管者的结果，而不是先回来的那份
        assertTrue(service.finishNotify(id, 2, tokenB), "当前持有者的结算必须生效");
        assertEquals(2, intColumn("notify_status", id), "必须留下 B 的真实结果，不能被 A 的旧结果抢占");
        assertNull(stringColumn("notify_token", id), "结算后必须清空租约令牌");
    }

    @Test
    @DisplayName("消息已被别人结算后，持有者的结算返回 false 且不改任何列")
    void finishNotifyIsRejectedWhenAlreadySettled() {
        long id = insertPendingMessage();
        String token = service.claimNotify(id, 0);
        assertNotNull(token);
        Timestamp claimedAt = timestampColumn("notify_time", id);

        // 10 分钟超时窗口把这条「发送中」的记录交给另一个实例并结算成失败
        db.update("UPDATE sys_message SET notify_status=2 WHERE id=?", id);

        assertFalse(service.finishNotify(id, 1, token), "状态已不是发送中，结算必须返回 false");

        assertEquals(2, intColumn("notify_status", id), "不得覆盖对方写入的结算状态");
        assertEquals(claimedAt, timestampColumn("notify_time", id), "结算被拒时不得改任何列");
        assertEquals(0, intColumn("is_read", id), "结算被拒时不得改任何列");
    }

    @Test
    @DisplayName("未经抢占的消息不允许结算")
    void finishNotifyRejectsMessageThatWasNeverClaimed() {
        long id = insertPendingMessage();

        assertFalse(service.finishNotify(id, 1, UUID.randomUUID().toString()));

        assertEquals(0, intColumn("notify_status", id));
        assertNull(timestampColumn("notify_time", id));
    }

    @Test
    @DisplayName("缺少租约令牌时拒绝结算，不退化成只按状态围栏结算")
    void finishNotifyRejectsMissingToken() {
        long id = insertPendingMessage();
        assertNotNull(service.claimNotify(id, 0), "先正常抢占，状态停在发送中");

        assertFalse(service.finishNotify(id, 1, null), "没有令牌的结算必须被拒绝");
        assertFalse(service.finishNotify(id, 1, "  "), "空白令牌同样必须被拒绝");

        assertEquals(3, intColumn("notify_status", id), "缺令牌的结算不得改动状态");
    }

    /**
     * 刻意的 characterization 用例：断言的是「缺陷存在时 save() 的表现」。
     *
     * <p>用途只有一个——把「为什么不能用 save 结算通知结果」钉成可执行文档：整实体回写会把
     * 投递期间用户写入的 is_read=1 覆盖回 0。它不是回归护栏，所以如果将来 {@code save()}
     * 的语义变安全（比如改成按字段条件更新），这条用例应当<b>同步改写或删除</b>，
     * 而不是当成「回归失败」去修实现。</p>
     */
    @Test
    @DisplayName("复现原缺陷：对旧快照调 save 会把投递期间的已读状态覆盖回未读")
    void savingStaleSnapshotOverwritesConcurrentReadState() {
        long id = insertPendingMessage();
        assertNotNull(service.claimNotify(id, 0));

        // 调用方在推送前读出的实体快照（is_read=0、notify_status=发送中），逐字段还原成旧值
        var snapshot = new SysMessage();
        snapshot.setId(id);
        snapshot.setIsRead(0);
        snapshot.setNeedNotify(1);
        snapshot.setTitle("库存不足: 电饭煲");
        snapshot.setContent("SKU 只剩 1 件");
        snapshot.setNotifyStatus(3);

        // 推送阻塞期间用户点了「标记已读」
        db.update("UPDATE sys_message SET is_read=1, read_time=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().withNano(0)), id);

        // 这就是修复前的写法：整实体回写
        service.save(snapshot);

        assertEquals(0, intColumn("is_read", id), "整快照回写把这个更新覆盖回了未读（lost update 根因）");
        // 因此结算只能走 finishNotify —— 见 finishNotifyOnlySettlesDeliveryAndKeepsConcurrentModifications
    }

    @Test
    @DisplayName("标记已读不改通知状态：不会把已结算的消息重新推回补偿窗口")
    void markReadDoesNotResetNotifyStatus() {
        long id = insertPendingMessage();                 // notify_status=0、is_read=0
        // 用户点「已读」时的读取（旧写法拿它做整实体回写）
        SysMessage snapshot = mapper.selectById(id);
        assertNotNull(snapshot, "消息必须存在");
        assertEquals(0, snapshot.getNotifyStatus().intValue(), "读取时恰好看到「待推送」");

        // 读取之后补偿任务把这条推成发送中并结算成功
        db.update("UPDATE sys_message SET notify_status=1, notify_time=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().withNano(0)), id);

        service.markRead(id);

        assertEquals(1, intColumn("is_read", id), "已读标记必须落库");
        assertNotNull(timestampColumn("read_time", id), "阅读时间必须落库");
        assertEquals(1, intColumn("notify_status", id),
                "已结算的通知状态不能被已读操作写回 0，否则会被 getFailedNotify 再次投递");

        // 复现原缺陷：把读到的旧快照整实体回写，notify_status 被改回 0（重新进入补偿窗口）
        mapper.updateById(snapshot);
        assertEquals(0, intColumn("notify_status", id),
                "整实体回写会把 notify_status 写回读取时的 0 —— 这正是 markRead 不能调 updateById 的原因");
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

    private String stringColumn(String column, long id) {
        return db.queryForObject("SELECT " + column + " FROM sys_message WHERE id=?", String.class, id);
    }

    private Timestamp timestampColumn(String column, long id) {
        return db.queryForObject("SELECT " + column + " FROM sys_message WHERE id=?", Timestamp.class, id);
    }
}
