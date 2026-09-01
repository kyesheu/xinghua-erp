package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

class AiRiskSummaryServiceTest {
    @Test void countsOnlyAuthorizedScopeAndCompleteDays() {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE o_refund(merchant_id BIGINT,shop_id BIGINT,create_time TIMESTAMP)");
        var end = LocalDate.now().atStartOfDay();
        db.update("INSERT INTO o_refund VALUES(7,10,?),(7,11,?),(8,12,?),(7,10,?),(7,10,?)", end.minusDays(7),end.minusDays(1),end.minusDays(1),end,end.minusDays(8));
        var service = new AiRiskSummaryService(db);
        assertEquals(3,service.overview(7,new BusinessSubjectScopeService.Scope("HEADQUARTERS",null,null,null)).refunds());
        assertEquals(2,service.overview(7,new BusinessSubjectScopeService.Scope("MERCHANT",7L,7L,null)).refunds());
        assertEquals(1,service.overview(7,new BusinessSubjectScopeService.Scope("SHOP",10L,7L,10L)).refunds());
        assertThrows(IllegalArgumentException.class,()->service.overview(7,new BusinessSubjectScopeService.Scope("WAREHOUSE",1L,null,null)));
        assertThrows(IllegalArgumentException.class,()->service.overview(7,new BusinessSubjectScopeService.Scope("SHOP",10L,null,10L)));
        db.execute("SHUTDOWN");
    }
}
