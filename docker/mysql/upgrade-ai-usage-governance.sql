CREATE TABLE IF NOT EXISTS ai_usage_bucket (
 bucket_key varchar(128) NOT NULL PRIMARY KEY,
 used_tokens bigint NOT NULL DEFAULT 0,
 requests bigint NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_call_request (
 request_id varchar(36) NOT NULL PRIMARY KEY,
 trace_id varchar(64) NOT NULL,
 user_id bigint NOT NULL,
 merchant_id bigint NOT NULL,
 bucket_day varchar(10) NOT NULL,
 path varchar(128) NOT NULL,
 model_name varchar(128) NOT NULL,
 reserved_tokens bigint NOT NULL,
 input_tokens bigint NULL,
 output_tokens bigint NULL,
 status varchar(16) NOT NULL,
 outcome varchar(32) NULL,
 created_at datetime NOT NULL,
 expires_at datetime NOT NULL,
 finished_at datetime NULL,
 KEY idx_ai_active(status,expires_at),
 KEY idx_ai_user_active(user_id,status,expires_at),
 KEY idx_ai_merchant_active(merchant_id,status,expires_at),
 KEY idx_ai_usage_created(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_circuit_state (
 circuit_key varchar(64) NOT NULL PRIMARY KEY,
 failures int NOT NULL DEFAULT 0,
 manual_disabled tinyint(1) NOT NULL DEFAULT 0,
 open_until datetime NULL,
 probe_until datetime NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
