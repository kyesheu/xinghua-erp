package cn.xinghuaerp.order.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 销售日报聚合的时间下界。
 * 原 SQL 没有任何时间条件却要做 GROUP BY DATE(order_time)，等于每次调用都全表聚合；
 * 加上下界后既能走 idx_ai_order_time，也仍然满足「最近 30 个有订单的日期」这个用法。
 */
class SalesDailyWindowTest {

    OOrderMapper mapper;
    JdbcTemplate db;

    @BeforeEach
    void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE o_order(id BIGINT, order_num VARCHAR(50), merchant_id BIGINT, shop_id BIGINT,"
                + " order_time TIMESTAMP, payment DECIMAL(18,2), amount DECIMAL(18,2), order_status INT)");

        LocalDateTime today = LocalDate.now().atStartOfDay().plusHours(10);
        insert(1L, "T1", 0L, today, 10.00);
        insert(2L, "T2", 0L, today.plusHours(2), 20.00);
        insert(3L, "D30", 0L, today.minusDays(30), 30.00);
        // 窗口外：超过 90 天
        insert(4L, "D100", 0L, today.minusDays(100), 40.00);
        // 另一商户，用于验证 scope 仍然生效
        insert(5L, "M101", 101L, today.plusHours(1), 50.00);

        MybatisConfiguration config = new MybatisConfiguration();
        config.addMapper(OOrderMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/oms/OOrderMapper.xml"));
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(OOrderMapper.class);
    }

    @AfterEach
    void close() {
        db.execute("SHUTDOWN");
    }

    private void insert(Long id, String num, Long merchantId, LocalDateTime time, double payment) {
        db.update("INSERT INTO o_order VALUES(?,?,?,?,?,?,?,?)", id, num, merchantId, 10L, time, payment, payment, 3);
    }

    @Test
    void dailyAggregateIsBoundedByWindowAndOrderedByDateDesc() {
        List<cn.xinghuaerp.order.vo.SalesDailyVo> days = mapper.salesDaily(null, null);

        // 今天三条（含商户 101 那条）聚合为一条 + 30 天前一条；100 天前那条落在窗口外
        assertEquals(2, days.size());
        assertEquals(LocalDate.now().toString(), days.get(0).getDate());
        assertEquals(3, days.get(0).getCount());
        assertEquals(80.00, days.get(0).getAmount(), 0.001);
        assertEquals(LocalDate.now().minusDays(30).toString(), days.get(1).getDate());
        assertEquals(30.00, days.get(1).getAmount(), 0.001);
    }

    /** 窗口外历史不能让日报无限回溯，否则每次都要扫全表。 */
    @Test
    void recordsOlderThanWindowAreExcluded() {
        boolean hasOldDay = mapper.salesDaily(null, null).stream()
                .anyMatch(day -> LocalDate.parse(day.getDate()).isBefore(LocalDate.now().minusDays(90)));
        assertFalse(hasOldDay, "超过 90 天的日期不应出现在日报里");
    }

    @Test
    void merchantScopeStillFiltersTheAggregate() {
        var scoped = mapper.salesDaily(101L, null);
        assertEquals(1, scoped.size());
        assertEquals(50.00, scoped.get(0).getAmount(), 0.001);
    }
}
