package cn.xinghuaerp.order.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 每日销售额统计的日期窗口与访问路径约束。
 * 用隔离 H2 真跑 mapper SQL，不连接业务库；同时用静态断言锁住「order_time 不能被函数包裹」，
 * 否则 o_order 上的 idx_ai_order_time 会失效退化成全表扫描。
 */
class OOrderDailySalesWindowTest {

    OOrderMapper mapper;
    JdbcTemplate db;

    @BeforeEach
    void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE o_order(id BIGINT, order_num VARCHAR(50), merchant_id BIGINT, shop_id BIGINT,"
                + " order_time TIMESTAMP, amount DECIMAL(18,2), order_status INT)");

        // 以「今天零点」为基准构造边界，避免用例随真实日期漂移
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        db.update("INSERT INTO o_order VALUES(?,?,?,?,?,?,?)", 1L, "T0", 0L, 10L, dayStart, 10.00, 3);
        db.update("INSERT INTO o_order VALUES(?,?,?,?,?,?,?)", 2L, "T12", 0L, 10L, dayStart.plusHours(12), 20.00, 3);
        db.update("INSERT INTO o_order VALUES(?,?,?,?,?,?,?)", 3L, "TLAST", 0L, 10L,
                dayStart.plusDays(1).minusSeconds(1), 30.00, 3);
        // 昨天最后一秒、明天零点：都不属于今天
        db.update("INSERT INTO o_order VALUES(?,?,?,?,?,?,?)", 4L, "YLAST", 0L, 10L,
                dayStart.minusSeconds(1), 100.00, 3);
        db.update("INSERT INTO o_order VALUES(?,?,?,?,?,?,?)", 5L, "TNEXT", 0L, 10L,
                dayStart.plusDays(1), 200.00, 3);
        // 商户维度：今天的另一商户订单，用于验证 scope 条件
        db.update("INSERT INTO o_order VALUES(?,?,?,?,?,?,?)", 6L, "T101", 101L, 20L, dayStart.plusHours(6), 40.00, 3);

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

    @Test
    void todayWindowIncludesWholeDayAndExcludesAdjacentDays() {
        var today = mapper.getTodaySalesDaily(null);
        // 今天 00:00:00、12:00、23:59:59 与商户 101 的 06:00 共 4 条计入；
        // 昨天最后一秒、明天零点都不计入
        assertEquals(4, today.getCount());
        assertEquals(100.00, today.getAmount(), 0.001);
    }

    @Test
    void merchantScopeStillAppliesToTheWindow() {
        var scoped = mapper.getTodaySalesDaily(101L);
        assertEquals(1, scoped.getCount());
        assertEquals(40.00, scoped.getAmount(), 0.001);
    }

    /** 零商户是虚拟总部，必须按「不限商户」处理，不能变成 merchant_id = 0。 */
    @Test
    void zeroMerchantIsTreatedAsUnscoped() {
        assertEquals(4, mapper.getTodaySalesDaily(0L).getCount());
    }

    /**
     * 索引可用性的静态护栏：WHERE 中若出现 DATE(order_time)、date_format(order_time,...) 这类
     * 对列做函数运算的写法，idx_ai_order_time 就无法用于范围定位。改 SQL 时由这条用例拦下。
     */
    @Test
    void orderTimeIsComparedDirectlyNotWrappedByFunctions() throws Exception {
        String xml;
        try (InputStream in = new ClassPathResource("mapper/oms/OOrderMapper.xml").getInputStream()) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = xml.indexOf("id=\"getTodaySalesDaily\"");
        assertTrue(start > 0, "getTodaySalesDaily 语句应存在");
        int end = xml.indexOf("</select>", start);
        String statement = xml.substring(start, end);

        Pattern wrappedByFunction = Pattern.compile("(?i)(date|date_format|year|month|to_days)\\s*\\(\\s*`?order_time");
        assertFalse(wrappedByFunction.matcher(statement).find(),
                "order_time 不能被函数包裹，否则索引失效：" + statement);
        assertTrue(statement.contains("order_time"), "必须显式使用 order_time 做范围过滤");
    }
}
