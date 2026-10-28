-- 已有数据库执行一次；新建数据库由主初始化 SQL 创建。
ALTER TABLE sys_message ADD COLUMN dedup_key varchar(100) DEFAULT NULL, ADD UNIQUE KEY uk_message_dedup (dedup_key);
CREATE TABLE IF NOT EXISTS order_event_outbox (
 message_id varchar(36) NOT NULL PRIMARY KEY,
 order_id bigint NOT NULL,
 payload text NOT NULL,
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 sent_at datetime NULL,
 attempts int NOT NULL DEFAULT 0,
 next_attempt_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_order_created (order_id),
 KEY idx_outbox_pending (sent_at, next_attempt_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
