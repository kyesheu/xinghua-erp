package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AiSessionAccessTest {
    JdbcTemplate jdbc; TransactionTemplate tx; AiSessionAccess access;
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));access=new AiSessionAccess(jdbc);
        jdbc.execute("CREATE TABLE ai_conversation_session(session_id varchar(64) PRIMARY KEY,user_id bigint NOT NULL)");
        jdbc.execute("CREATE TABLE ai_conversation_history(session_id varchar(64),user_id bigint)");
    }
    @Test void anotherUserCannotClaimOrReadExistingSession() {
        tx.executeWithoutResult(s -> access.claim("session",7L));
        assertThrows(AccessDeniedException.class, () -> tx.executeWithoutResult(s -> access.claim("session",8L)));
        assertThrows(AccessDeniedException.class, () -> access.assertOwner("session",8L));
        assertDoesNotThrow(() -> access.assertOwner("session",7L));
    }
    @Test void legacySessionCannotBeClaimedByAnotherUserAndFailedClaimRollsBack() {
        jdbc.update("INSERT INTO ai_conversation_history VALUES('legacy',7)");
        assertThrows(AccessDeniedException.class, () -> tx.executeWithoutResult(s -> access.claim("legacy",8L)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_session",Integer.class));
        tx.executeWithoutResult(s -> access.claim("legacy",7L));
    }
    @Test void concurrentFirstClaimHasOneOwner() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var jobs=new java.util.ArrayList<Future<Boolean>>();
            for(long user: new long[]{7,8}) jobs.add(pool.submit(() -> {
                start.await();
                try { tx.executeWithoutResult(s -> access.claim("race",user)); return true; }
                catch(AccessDeniedException expected) { return false; }
            }));
            start.countDown();int allowed=0;for(var job:jobs) if(job.get(10,TimeUnit.SECONDS))allowed++;
            assertEquals(1,allowed);
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_session",Integer.class));
    }
    @Test void mixedOrUnownedLegacyDataIsRejected() {
        jdbc.update("INSERT INTO ai_conversation_history VALUES('mixed',7),('mixed',8),('unknown',NULL)");
        assertThrows(AccessDeniedException.class, () -> access.assertOwner("mixed",7L));
        assertThrows(AccessDeniedException.class, () -> access.assertOwner("unknown",7L));
    }
    @Test void deletingUnknownSessionDoesNotReserveArbitraryIdentifiers() {
        tx.executeWithoutResult(status->assertFalse(access.lockForDelete("unknown",7L)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_session",Integer.class));
        tx.executeWithoutResult(status->access.claim("mine",7L));
        assertThrows(AccessDeniedException.class,()->tx.executeWithoutResult(status->access.lockForDelete("mine",8L)));
    }
}
