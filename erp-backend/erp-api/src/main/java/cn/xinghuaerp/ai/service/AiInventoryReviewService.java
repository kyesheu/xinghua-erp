package cn.xinghuaerp.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiInventoryReviewService {
    private final JdbcTemplate jdbc;
    public record Task(String id, long createdBy, long skuId, long warehouseId, String requestJson, String factsJson,
                       String resultJson, int suggestedQuantity, String status, int version, LocalDateTime createdAt,
                       Long reviewedBy, LocalDateTime reviewedAt, String reviewNote, Integer approvedQuantity) {}
    public record Summary(String id, long createdBy, long skuId, long warehouseId, int suggestedQuantity,
                          String status, int version, LocalDateTime createdAt) {}

    public Task findByKey(long userId, String key) {
        var rows = jdbc.query("SELECT * FROM ai_inventory_review_task WHERE created_by=? AND request_key=?", (rs,n)->task(rs),userId,key);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public String create(long userId, String key, long skuId, long warehouseId, String requestJson, String factsJson, String resultJson, int quantity) {
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO ai_inventory_review_task(id,created_by,request_key,sku_id,warehouse_id,request_json,facts_json,result_json,suggested_quantity,status,version,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,userId,key,skuId,warehouseId,requestJson,factsJson,resultJson,quantity,"PENDING",1,LocalDateTime.now());
        } catch (org.springframework.dao.DuplicateKeyException error) {
            var existing = findByKey(userId,key);
            if (existing == null || !existing.requestJson().equals(requestJson)) throw new IllegalStateException("请求标识已用于其他审核内容");
            return existing.id();
        }
        return id;
    }
    public List<Summary> recent() {
        return jdbc.query("SELECT id,created_by,sku_id,warehouse_id,suggested_quantity,status,version,created_at FROM ai_inventory_review_task ORDER BY created_at DESC,id DESC LIMIT 50",
                (rs,n)->new Summary(rs.getString("id"),rs.getLong("created_by"),rs.getLong("sku_id"),rs.getLong("warehouse_id"),
                        rs.getInt("suggested_quantity"),rs.getString("status"),rs.getInt("version"),rs.getTimestamp("created_at").toLocalDateTime()));
    }
    public List<Task> pendingWorkflowSync() {
        return jdbc.query("SELECT * FROM ai_inventory_review_task WHERE workflow_synced_version < version ORDER BY created_at,id LIMIT 20", (rs,n)->task(rs));
    }
    public void markWorkflowSynced(String id, int version) {
        jdbc.update("UPDATE ai_inventory_review_task SET workflow_synced_version=? WHERE id=? AND version=?",version,id,version);
    }
    public Task get(String id) {
        var rows = jdbc.query("SELECT * FROM ai_inventory_review_task WHERE id=?", (rs,n)->task(rs), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("审核任务不存在");
        return rows.get(0);
    }
    public void decide(String id, int version, boolean approved, long reviewer, String note, Integer approvedQuantity) {
        int changed = jdbc.update("UPDATE ai_inventory_review_task SET status=?,version=version+1,reviewed_by=?,reviewed_at=?,review_note=?,approved_quantity=? WHERE id=? AND version=? AND status='PENDING'",
                approved ? "APPROVED" : "REJECTED", reviewer, LocalDateTime.now(), note, approvedQuantity, id, version);
        if (changed != 1) throw new IllegalStateException("审核任务已变化，请刷新后重试");
    }
    private Task task(java.sql.ResultSet rs) throws java.sql.SQLException {
        var reviewedAt = rs.getTimestamp("reviewed_at");
        return new Task(rs.getString("id"),rs.getLong("created_by"),rs.getLong("sku_id"),rs.getLong("warehouse_id"),
                rs.getString("request_json"),rs.getString("facts_json"),rs.getString("result_json"),rs.getInt("suggested_quantity"),
                rs.getString("status"),rs.getInt("version"),rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getObject("reviewed_by",Long.class),reviewedAt == null ? null : reviewedAt.toLocalDateTime(),rs.getString("review_note"),rs.getObject("approved_quantity",Integer.class));
    }
}
