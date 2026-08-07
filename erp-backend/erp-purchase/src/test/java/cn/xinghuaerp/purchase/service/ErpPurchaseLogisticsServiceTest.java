package cn.xinghuaerp.purchase.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import cn.xinghuaerp.purchase.mapper.ErpPurchaseLogisticsMapper;
import cn.xinghuaerp.purchase.entity.ErpPurchaseLogistics;
import cn.xinghuaerp.purchase.service.impl.ErpPurchaseLogisticsServiceImpl;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ErpPurchaseLogisticsServiceImpl#existsByCode(String, Long)} 的真实 SQL 校验：
 * 用独立 H2 内存库跑两种分支（新增场景 {@code excludeId = null}、修改场景排除自身主键），
 * 确保去向重校验在“编码未变时编辑自身”和“编码被他人占用”两种情况下结论正确。
 */
class ErpPurchaseLogisticsServiceTest {

    /** 测试用子类：ServiceImpl 的 baseMapper 是 protected 字段，借子类注入真实 Mapper。 */
    private static final class TestableService extends ErpPurchaseLogisticsServiceImpl {
        void bind(ErpPurchaseLogisticsMapper mapper) {
            this.baseMapper = mapper;
        }
    }

    private JdbcTemplate db;
    private TestableService service;

    @BeforeEach
    void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE erp_purchase_logistics ("
                + "id bigint not null auto_increment primary key,"
                + "code varchar(255),"
                + "name varchar(255),"
                + "remark varchar(255),"
                + "status int,"
                + "merchant_id bigint not null default 0,"
                + "shop_id bigint not null default 0)");
        db.update("INSERT INTO erp_purchase_logistics(id,code,name,status) VALUES(1,'SF','顺丰速运',1)");
        db.update("INSERT INTO erp_purchase_logistics(id,code,name,status) VALUES(2,'YTO','圆通速递',1)");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(ErpPurchaseLogisticsMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(configuration);
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ErpPurchaseLogistics.class);
        service = new TestableService();
        service.bind(session.getMapper(ErpPurchaseLogisticsMapper.class));
    }

    @AfterEach
    void close() {
        db.execute("SHUTDOWN");
    }

    @Test
    void addBranchUsesCodeOnlyFilter() {
        assertTrue(service.existsByCode("SF", null), "新增时编码已存在必须判重成功");
        assertTrue(service.existsByCode("YTO", null));
        assertFalse(service.existsByCode("NEW", null), "不存在的编码不应误判重");
    }

    @Test
    void editBranchExcludesOwnRowOnly() {
        // 编码未变时编辑自身：排除自己后不应判重，否则“只改备注”会被拦截
        assertFalse(service.existsByCode("SF", 1L));
        // 编码改成别人已占用的编码：仍要判重
        assertTrue(service.existsByCode("YTO", 1L));
        // 排除一个与编码无关的主键时不影响判重结论
        assertTrue(service.existsByCode("SF", 999L));
    }

    @Test
    void statusUpdateOnlyTouchesTargetRow() {
        assertTrue(service.updateStatus(2L, 0) > 0);
        Integer status = db.queryForObject(
                "SELECT status FROM erp_purchase_logistics WHERE id = 2", Integer.class);
        Integer other = db.queryForObject(
                "SELECT status FROM erp_purchase_logistics WHERE id = 1", Integer.class);
        assertTrue(Integer.valueOf(0).equals(status));
        assertTrue(Integer.valueOf(1).equals(other), "其他承运商的状态不应被连带修改");
    }
}
