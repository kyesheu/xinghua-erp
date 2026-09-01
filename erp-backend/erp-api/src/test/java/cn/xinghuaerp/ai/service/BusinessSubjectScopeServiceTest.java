package cn.xinghuaerp.ai.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import cn.xinghuaerp.inventory.mapper.ErpWarehouseMapper;
import cn.xinghuaerp.system.mapper.ErpMerchantMapper;
import cn.xinghuaerp.system.mapper.OShopMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.system.mapper.BusinessUserBindingMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;
import cn.xinghuaerp.inventory.service.ErpWarehouseSubjectValidator;
import cn.xinghuaerp.purchase.service.ErpSupplierSubjectValidator;

/** 仅使用隔离 H2 和真实 Mapper；不访问用户数据库。 */
class BusinessSubjectScopeServiceTest {
    JdbcTemplate db;
    BusinessSubjectScopeService scope;
    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("business-subject-schema.sql")).execute(ds);
        db = new JdbcTemplate(ds); var config = new MybatisConfiguration();
        for (var mapper : List.of(BusinessUserBindingMapper.class,ErpMerchantMapper.class,OShopMapper.class,ErpSupplierMapper.class,ErpWarehouseMapper.class)) config.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        var target = new BusinessSubjectScopeService(
                session.getMapper(BusinessUserBindingMapper.class),
                session.getMapper(ErpMerchantMapper.class),
                session.getMapper(OShopMapper.class),
                List.of(
                        new cn.xinghuaerp.purchase.service.ErpSupplierSubjectValidator(session.getMapper(ErpSupplierMapper.class)),
                        new cn.xinghuaerp.inventory.service.ErpWarehouseSubjectValidator(session.getMapper(ErpWarehouseMapper.class))));
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        scope = (BusinessSubjectScopeService) proxy.getProxy();
        db.update("INSERT INTO sys_user VALUES(1,'00','0','0'),(2,'20','0','0'),(3,'40','0','0'),(4,'30','0','0'),(5,'10','0','0')");
        db.update("INSERT INTO erp_merchant VALUES(7,'0','0'),(8,'0','0')");
        db.update("INSERT INTO o_shop VALUES(10,'1',7),(11,'1',0)");
        db.update("INSERT INTO erp_supplier VALUES(20,0,0)"); db.update("INSERT INTO erp_warehouse VALUES(30,'1')");
        db.update("INSERT INTO erp_business_user_binding(user_id,subject_type,subject_id,enabled) VALUES(2,'MERCHANT',7,1),(3,'SHOP',10,1),(4,'SUPPLIER',20,1),(5,'WAREHOUSE',30,1)");
    }
    @Test void headquartersAndBoundSubjectsResolveWithoutUsingDepartmentIds() {
        assertEquals("HEADQUARTERS",scope.resolve(1L).type());
        assertEquals(7L,scope.resolve(2L).merchantId());
        var shop = scope.resolve(3L); assertEquals(7L,shop.merchantId()); assertEquals(10L,shop.shopId());
        assertEquals(20L,scope.resolve(4L).subjectId()); assertEquals(30L,scope.resolve(5L).subjectId());
    }
    @Test void missingDisabledAndWrongTypeBindingsFailClosed() {
        db.update("DELETE FROM erp_business_user_binding WHERE user_id=2"); assertThrows(IllegalArgumentException.class,()->scope.resolve(2L));
        db.update("UPDATE erp_business_user_binding SET enabled=0 WHERE user_id=3"); assertThrows(IllegalArgumentException.class,()->scope.resolve(3L));
        db.update("UPDATE erp_business_user_binding SET subject_type='MERCHANT' WHERE user_id=4"); assertThrows(IllegalArgumentException.class,()->scope.resolve(4L));
    }
    @Test void bindingAndOwnershipChangesAreReadOnEveryResolution() {
        assertEquals(7L,scope.resolve(3L).merchantId()); db.update("UPDATE o_shop SET merchant_id=8 WHERE id=10"); assertEquals(8L,scope.resolve(3L).merchantId());
        db.update("UPDATE erp_business_user_binding SET subject_id=11 WHERE user_id=3"); assertEquals(0L,scope.resolve(3L).merchantId());
        db.update("UPDATE erp_business_user_binding SET subject_id=999 WHERE user_id=3"); assertThrows(IllegalArgumentException.class,()->scope.resolve(3L));
    }
    @Test void disabledUsersAndBusinessSubjectsCannotResolve() {
        db.update("UPDATE sys_user SET status='1' WHERE user_id=1"); assertThrows(IllegalArgumentException.class,()->scope.resolve(1L));
        db.update("UPDATE erp_merchant SET status='1' WHERE id=7"); assertThrows(IllegalArgumentException.class,()->scope.resolve(2L)); assertThrows(IllegalArgumentException.class,()->scope.resolve(3L));
        db.update("UPDATE erp_supplier SET disable=1 WHERE id=20"); assertThrows(IllegalArgumentException.class,()->scope.resolve(4L));
        db.update("UPDATE erp_warehouse SET status='0' WHERE id=30"); assertThrows(IllegalArgumentException.class,()->scope.resolve(5L));
    }
    @Test void userTypeChangeDoesNotInheritPreviousBinding() {
        db.update("UPDATE sys_user SET user_type='40' WHERE user_id=2"); assertThrows(IllegalArgumentException.class,()->scope.resolve(2L));
        db.update("UPDATE sys_user SET del_flag='2' WHERE user_id=5"); assertThrows(IllegalArgumentException.class,()->scope.resolve(5L));
        assertThrows(IllegalArgumentException.class,()->scope.resolve(999L));
    }
    @Test void bindingManagementValidatesAndChangesTakeEffect() {
        scope.bind(2L,"MERCHANT",8L,"admin");
        assertEquals(8L,scope.resolve(2L).merchantId());
        assertEquals("admin",scope.binding(2L).getUpdateBy());
        assertThrows(IllegalArgumentException.class,()->scope.bind(2L,"SHOP",10L,"admin"));
        assertThrows(IllegalArgumentException.class,()->scope.bind(2L,"MERCHANT",999L,"admin"));
        assertThrows(IllegalArgumentException.class,()->scope.bind(1L,"MERCHANT",7L,"admin"));
        assertEquals(8L,scope.resolve(2L).merchantId());
        scope.unbind(2L,"admin"); assertThrows(IllegalArgumentException.class,()->scope.resolve(2L));
        scope.bind(2L,"MERCHANT",7L,"admin"); assertEquals(7L,scope.resolve(2L).merchantId());
        db.update("DELETE FROM erp_business_user_binding WHERE user_id=4");
        scope.bind(4L,"SUPPLIER",20L,"creator");
        assertEquals("creator",scope.binding(4L).getCreateBy());
        db.update("UPDATE sys_user SET status='1' WHERE user_id=4");
        assertThrows(IllegalArgumentException.class,()->scope.bind(4L,"SUPPLIER",20L,"admin"));
    }
    @Test void concurrentFirstBindingsSerializeOnUserRow() throws Exception {
        db.update("DELETE FROM erp_business_user_binding WHERE user_id=2");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); scope.bind(2L,"MERCHANT",7L,"first"); return null; });
            var second = pool.submit(() -> { start.await(); scope.bind(2L,"MERCHANT",8L,"second"); return null; });
            start.countDown(); first.get(); second.get();
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM erp_business_user_binding WHERE user_id=2",Integer.class));
            assertTrue(List.of(7L,8L).contains(scope.resolve(2L).merchantId()));
        } finally { pool.shutdownNow(); }
    }
}
