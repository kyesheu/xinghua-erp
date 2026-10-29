package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AiInventoryReviewServiceTest {
    @Test void persistsSnapshotAndAcceptsOnlyOneVersionedDecision() {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE ai_inventory_review_task(id VARCHAR(36) PRIMARY KEY,created_by BIGINT,request_key VARCHAR(36),sku_id BIGINT,warehouse_id BIGINT,request_json CLOB,facts_json CLOB,result_json CLOB,suggested_quantity INT,approved_quantity INT,status VARCHAR(16),version INT,created_at TIMESTAMP,reviewed_by BIGINT,reviewed_at TIMESTAMP,review_note VARCHAR(500), UNIQUE(created_by,request_key))");
        var service = new AiInventoryReviewService(db);
        var id = service.create(7,"key",5,1,"{\"days\":7}","{\"stock\":8}","{\"suggestedQuantity\":5}",5);
        var task = service.get(id);
        assertEquals(id,service.create(7,"key",5,1,"{\"days\":7}","{}","{}",5));
        assertThrows(IllegalStateException.class,()->service.create(7,"key",5,1,"{}","{}","{}",5));
        assertEquals("PENDING",task.status()); assertEquals(1,task.version());
        assertEquals("{\"stock\":8}",task.factsJson());
        service.decide(id,1,true,9,"核对通过",7);
        var decided = service.get(id);
        assertEquals(7,decided.approvedQuantity()); assertEquals(5,decided.suggestedQuantity());
        assertEquals("APPROVED",decided.status()); assertEquals(2,decided.version()); assertEquals(9,decided.reviewedBy());
        assertThrows(IllegalStateException.class,()->service.decide(id,1,false,10,"过期",null));
        assertEquals("APPROVED",service.recent().get(0).status());
        var second = service.create(7,"second",5,1,"{}","{}","{}",5);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(); try { service.decide(second,1,true,9,"",5); return true; } catch (IllegalStateException e) { return false; } });
            var b = pool.submit(() -> { start.await(); try { service.decide(second,1,false,10,"",null); return true; } catch (IllegalStateException e) { return false; } });
            start.countDown();
            assertNotEquals(a.get(5,java.util.concurrent.TimeUnit.SECONDS),b.get(5,java.util.concurrent.TimeUnit.SECONDS));
        } catch (Exception e) { throw new AssertionError(e); } finally { pool.shutdownNow(); }
        db.execute("SHUTDOWN");
    }
    @Test void failedFirstPageDoesNotHideLaterPendingTasksAndScanWraps() throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new JdbcTemplate(ds);
        java.nio.file.Path root=java.nio.file.Path.of("").toAbsolutePath();while(!java.nio.file.Files.exists(root.resolve("docs/sql/ai-inventory-review.sql")))root=root.getParent();
        String ddl=java.nio.file.Files.readString(root.resolve("docs/sql/ai-inventory-review.sql"));
        String create=ddl.substring(ddl.indexOf("CREATE TABLE"),ddl.indexOf(";",ddl.indexOf("CREATE TABLE")));
        create=create.replaceAll("(?s)ENGINE=.*", "");db.execute(create);
        var service=new AiInventoryReviewService(db);
        for(int i=1;i<=25;i++)db.update("INSERT INTO ai_inventory_review_task(id,created_by,request_key,sku_id,warehouse_id,request_json,facts_json,result_json,suggested_quantity,status,version,created_at) VALUES(?,7,?,1,1,'{}','{}','{}',1,'PENDING',1,CURRENT_TIMESTAMP)",String.format("%036d",i),"key"+i);
        var first=service.pendingWorkflowSync("");assertEquals(20,first.size());
        var next=service.pendingWorkflowSync(first.getLast().id());assertEquals(5,next.size());
        assertEquals(first.getFirst().id(),service.pendingWorkflowSync(next.getLast().id()).getFirst().id());
        db.execute("SHUTDOWN");
    }
}
