package cn.xinghuaerp.erp.task;

import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.mapper.SysMessageMapper;
import cn.xinghuaerp.system.notify.NotifierService;
import cn.xinghuaerp.system.service.impl.SysMessageServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 消息补偿任务的多实例并发边界验证。
 *
 * <p>两个 {@link MessageScheduler} 实例共享同一份 H2 消息表并发跑 {@code retryFailedNotify()}，
 * 断言每条消息只被投递一次；同时验证批次边界（LIMIT 20）上的游标推进与尾部回绕：
 * 失败记录只把自己推到队尾，不阻塞后续记录，等下一轮超时窗口再回来。</p>
 */
class MessageSchedulerMultiInstanceTest {

    private static final Pattern HOLES =
            Pattern.compile("#\\{ew\\.paramNameValuePairs\\.([A-Za-z0-9_]+)}");

    /** 把 Wrapper 片段还原成可执行 SQL，并按占位符出现顺序收集参数。 */
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

    static String where(Wrapper<?> wrapper, List<Object> values) {
        var raw = (AbstractWrapper<?, ?, ?>) wrapper;
        String segment = bind(raw.getSqlSegment(), raw.getParamNameValuePairs(), values);
        return segment.isBlank() ? "" : " WHERE " + segment;
    }

    /** 让 n 个线程尽量同时起跑；不依赖固定睡眠，只用 latch + 有限的 get 超时。 */
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

    /** 共享消息表 + 真实条件更新的消息服务，模拟多实例访问同一套数据库。 */
    static final class Fixture {
        final JdbcTemplate db;
        final SysMessageServiceImpl messageService;
        final ConcurrentLinkedQueue<String> delivered = new ConcurrentLinkedQueue<>();
        private final Set<String> brokenTitles;

        Fixture(String database, Set<String> brokenTitles) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), database), SysMessage.class);
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            this.db = new JdbcTemplate(ds);
            db.execute("CREATE TABLE sys_message(id BIGINT AUTO_INCREMENT PRIMARY KEY,dedup_key VARCHAR(128),"
                    + "type VARCHAR(32),title VARCHAR(128),content VARCHAR(512),is_read INT,need_notify INT,"
                    + "notify_status INT,notify_time TIMESTAMP,created_time TIMESTAMP)");
            this.messageService = new SysMessageServiceImpl(mapper());
            this.brokenTitles = brokenTitles;
        }

        NotifierService notifier() {
            var notifier = mock(NotifierService.class);
            when(notifier.notifyAll(any(), any())).thenAnswer(call -> {
                String title = call.getArgument(0, String.class);
                if (brokenTitles.contains(title)) throw new IllegalStateException("channel offline");
                delivered.add(title);
                return true;
            });
            return notifier;
        }

        MessageScheduler scheduler() {
            return new MessageScheduler(null, null, messageService, notifier(), null);
        }

        void seed(String title, Integer notifyStatus, LocalDateTime notifyTime, LocalDateTime createdTime) {
            db.update("INSERT INTO sys_message(type,title,content,is_read,need_notify,notify_status,"
                            + "notify_time,created_time) VALUES(?,?,?,?,?,?,?,?)",
                    "order_timeout", title, "content-" + title, 0, 1, notifyStatus,
                    notifyTime == null ? null : java.sql.Timestamp.valueOf(notifyTime),
                    java.sql.Timestamp.valueOf(createdTime));
        }

        int countDelivered(String title) {
            return (int) delivered.stream().filter(title::equals).count();
        }

        Integer notifyStatus(String title) {
            return db.queryForObject("SELECT notify_status FROM sys_message WHERE title=?", Integer.class, title);
        }

        LocalDateTime notifyTime(String title) {
            java.sql.Timestamp value = db.queryForObject(
                    "SELECT notify_time FROM sys_message WHERE title=?", java.sql.Timestamp.class, title);
            return value == null ? null : value.toLocalDateTime();
        }

        void shutdown() {
            db.execute("SHUTDOWN");
        }

        /**
         * 只翻译 Wrapper 的 Mapper 桩：selectList 走真实 SQL 查询，update 走真实条件更新，
         * 因此"影响行数=1 才算抢到"的并发语义来自数据库而不是 mock 的返回值。
         */
        private SysMessageMapper mapper() {
            var mapper = mock(SysMessageMapper.class);
            when(mapper.selectList(any())).thenAnswer(call -> {
                List<Object> values = new ArrayList<>();
                String sql = "SELECT id,title,content,need_notify,notify_status,notify_time,created_time"
                        + " FROM sys_message" + where(call.getArgument(0, Wrapper.class), values);
                return db.query(sql, MESSAGE_MAPPER, values.toArray());
            });
            when(mapper.update(isNull(), any())).thenAnswer(call -> {
                var raw = (AbstractWrapper<?, ?, ?>) call.getArgument(1, Wrapper.class);
                List<Object> values = new ArrayList<>();
                String setSql = bind(raw.getSqlSet(), raw.getParamNameValuePairs(), values);
                String whereSql = bind(raw.getSqlSegment(), raw.getParamNameValuePairs(), values);
                return db.update("UPDATE sys_message SET " + setSql
                        + (whereSql.isBlank() ? "" : " WHERE " + whereSql), values.toArray());
            });
            when(mapper.updateById(any(SysMessage.class))).thenAnswer(call -> {
                SysMessage msg = call.getArgument(0, SysMessage.class);
                return db.update("UPDATE sys_message SET notify_status=?,notify_time=? WHERE id=?",
                        msg.getNotifyStatus(),
                        msg.getNotifyTime() == null ? null
                                : java.sql.Timestamp.valueOf(msg.getNotifyTime()), msg.getId());
            });
            return mapper;
        }
    }

    private static final RowMapper<SysMessage> MESSAGE_MAPPER = (rs, n) -> {
        var msg = new SysMessage();
        msg.setId(rs.getLong("id"));
        msg.setTitle(rs.getString("title"));
        msg.setContent(rs.getString("content"));
        msg.setNeedNotify(rs.getInt("need_notify"));
        msg.setNotifyStatus(rs.getInt("notify_status"));
        java.sql.Timestamp notifyTime = rs.getTimestamp("notify_time");
        msg.setNotifyTime(notifyTime == null ? null : notifyTime.toLocalDateTime());
        java.sql.Timestamp createdTime = rs.getTimestamp("created_time");
        msg.setCreatedTime(createdTime == null ? null : createdTime.toLocalDateTime());
        return msg;
    };

    @Test
    void twoSchedulerInstancesDeliverEachMessageExactlyOnce() throws Exception {
        var fixture = new Fixture("message_multi_instance", Set.of());
        var now = LocalDateTime.now();
        for (int i = 1; i <= 5; i++) {
            fixture.seed("msg-" + i, 0, null, now.minusMinutes(30));
        }
        var instanceA = fixture.scheduler();
        var instanceB = fixture.scheduler();

        List<String> outcomes = race(2, index -> {
            try {
                (index == 0 ? instanceA : instanceB).retryFailedNotify();
                return "done";
            } catch (RuntimeException failure) {
                return "error:" + failure.getClass().getSimpleName();
            }
        });

        assertEquals(2, outcomes.size());
        for (int i = 1; i <= 5; i++) {
            assertEquals(1, fixture.countDelivered("msg-" + i),
                    "消息 msg-" + i + " 必须只被投递一次，实际投递记录：" + fixture.delivered);
        }
        assertEquals(5, fixture.delivered.size(), "总投递次数必须等于待补偿消息条数：" + fixture.delivered);
        for (int i = 1; i <= 5; i++) {
            assertEquals(1, fixture.notifyStatus("msg-" + i).intValue(),
                    "投递成功后状态必须回到成功，实际：" + fixture.notifyStatus("msg-" + i));
        }
        fixture.shutdown();
    }

    @Test
    void cursorAdvancesPastFailedMessageAndWrapsAroundBatchBoundary() throws Exception {
        var fixture = new Fixture("message_cursor_rotation", Set.of("boom"));
        var now = LocalDateTime.now();
        // 20 条更早的消息占满一个批次（LIMIT 20），其中 boom 推送必然失败。
        for (int i = 1; i <= 20; i++) {
            String title = i == 20 ? "boom" : "batch-" + i;
            fixture.seed(title, 0, now.minusMinutes(30), now.minusMinutes(31));
        }
        // 5 条稍晚的消息，第一轮排不进批次，必须由第二轮补上（尾部回绕）。
        for (int i = 1; i <= 5; i++) {
            fixture.seed("late-" + i, 0, now.minusMinutes(25), now.minusMinutes(26));
        }
        var scheduler = fixture.scheduler();

        scheduler.retryFailedNotify();

        assertEquals(0, fixture.countDelivered("boom"), "boom 必然推送失败，不该计入成功投递");
        for (int i = 1; i <= 19; i++) {
            assertEquals(1, fixture.countDelivered("batch-" + i), "单条失败不得阻塞同批其他消息");
        }
        for (int i = 1; i <= 5; i++) {
            assertEquals(0, fixture.countDelivered("late-" + i), "第一轮受批次上限限制，不应越界处理");
        }
        assertEquals(SysMessageServiceImpl.NOTIFY_STATUS_SENDING, fixture.notifyStatus("boom").intValue(),
                "抢占后推送异常的消息停留在发送中，由 10 分钟超时窗口自愈，不会与本轮其他消息纠缠");
        LocalDateTime advanced = fixture.notifyTime("boom");
        assertTrue(advanced != null && advanced.isAfter(now.minusMinutes(1)),
                "失败记录必须推进自己的补偿时间，避免同一轮反复重试");

        scheduler.retryFailedNotify();

        for (int i = 1; i <= 5; i++) {
            assertEquals(1, fixture.countDelivered("late-" + i), "第二轮必须回绕处理剩余消息");
        }
        assertEquals(0, fixture.countDelivered("boom"), "失败记录的补偿时间已推进，本轮不应再被选中");
        Set<String> distinct = new HashSet<>(fixture.delivered);
        assertEquals(fixture.delivered.size(), distinct.size(), "任何消息都不允许两次投递：" + fixture.delivered);
        assertEquals(24, fixture.delivered.size(), "两轮共应投递 24 条（20 条成功 19 条 + 剩余 5 条）");
        fixture.shutdown();
    }
}
