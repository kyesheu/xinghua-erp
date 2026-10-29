CREATE TABLE IF NOT EXISTS ai_conversation_session (
 session_id varchar(64) NOT NULL PRIMARY KEY,
 user_id bigint NOT NULL,
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_ai_session_user (user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
