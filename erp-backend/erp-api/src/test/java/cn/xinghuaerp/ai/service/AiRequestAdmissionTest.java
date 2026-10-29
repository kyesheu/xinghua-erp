package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiRequestAdmissionTest {
    JdbcTemplate jdbc; AiRequestAdmission admission; ToolToken token;
    @BeforeEach void setup() throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(ds);
        Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("docker/mysql/upgrade-ai-usage-governance.sql")))root=root.getParent();
        for(String sql:Files.readString(root.resolve("docker/mysql/upgrade-ai-usage-governance.sql")).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "").split(";"))if(!sql.isBlank())jdbc.execute(sql);
        token=new ToolToken("secret-for-offline-admission-tests-only");
        var access=mock(BusinessAccessService.class);when(access.scope(anyLong())).thenReturn(new Scope("MERCHANT",1L,1L,null));
        admission=new AiRequestAdmission(jdbc,new DataSourceTransactionManager(ds),token,access);
    }
    AiRequestAdmission.Permit begin(){return admission.begin(token.issue(7L),"/internal/chat","fake-model");}
    @Test void settlementRefundsDifferenceExactlyOnce() {
        var permit=begin();admission.finish(permit,100L,10L,true,"COMPLETED");
        admission.finish(permit,0L,0L,true,"COMPLETED");
        assertEquals(330L,jdbc.queryForObject("SELECT SUM(used_tokens) FROM ai_usage_bucket",Long.class));
        assertEquals("SETTLED",jdbc.queryForObject("SELECT status FROM ai_call_request",String.class));
    }
    @Test void unknownUsageKeepsReservationAndDailyBudgetEventuallyRejects() {
        for(int i=0;i<4;i++) {var permit=begin();admission.finish(permit,null,null,false,"DISCONNECTED");}
        assertThrows(IllegalStateException.class,this::begin);
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM ai_call_request WHERE status='UNKNOWN'",Integer.class));
    }
    @Test void frequencyCannotBeBypassedByRefundingKnownZeroUsage() {
        for(int i=0;i<10;i++){var permit=begin();admission.finish(permit,0L,0L,true,"RULES");}
        assertThrows(IllegalStateException.class,this::begin);
        assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM ai_call_request",Integer.class));
    }
    @Test void concurrentRequestsShareInFlightLimit() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(3)) {
            var jobs=new java.util.ArrayList<Future<Boolean>>();
            for(int i=0;i<3;i++)jobs.add(pool.submit(()->{start.await();try{begin();return true;}catch(IllegalStateException expected){return false;}}));
            start.countDown();int allowed=0;for(var job:jobs)if(job.get(10,TimeUnit.SECONDS))allowed++;
            assertEquals(2,allowed);
        }
    }
    @Test void expiredLeaseReleasesConcurrencyButNotReservedTokens() {
        begin();begin();assertThrows(IllegalStateException.class,this::begin);
        jdbc.update("UPDATE ai_call_request SET expires_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        assertNotNull(begin());
        assertEquals(3*3*220000L,jdbc.queryForObject("SELECT SUM(used_tokens) FROM ai_usage_bucket",Long.class));
    }
    @Test void repeatedUpstreamFailureOpensAndSingleProbeRecovers() {
        for(int i=0;i<3;i++){var permit=begin();admission.finish(permit,0L,0L,true,"UPSTREAM_ERROR");}
        assertThrows(IllegalStateException.class,this::begin);
        jdbc.update("UPDATE ai_circuit_state SET open_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        var probe=begin();
        assertThrows(IllegalStateException.class,this::begin);
        admission.finish(probe,0L,0L,true,"COMPLETED");
        assertNotNull(begin());
    }
    @Test void manualStopIsSharedAndDoesNotEraseExistingBudget() {
        var permit=begin();admission.finish(permit,100L,10L,true,"COMPLETED");
        admission.setEnabled(false);assertThrows(IllegalStateException.class,this::begin);
        admission.setEnabled(true);assertNotNull(begin());
        assertEquals(3*(220000L+110L),jdbc.queryForObject("SELECT SUM(used_tokens) FROM ai_usage_bucket",Long.class));
    }
}
