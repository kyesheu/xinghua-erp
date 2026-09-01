-- 业务主体解析相关的最小表结构（H2），仅供 BusinessSubjectScopeServiceTest 使用。
-- 列顺序与测试中的 INSERT ... VALUES 保持一致。

CREATE TABLE IF NOT EXISTS sys_user (
  user_id   BIGINT       PRIMARY KEY,
  user_type VARCHAR(8),
  status    CHAR(1),
  del_flag  CHAR(1)
);

CREATE TABLE IF NOT EXISTS erp_merchant (
  id       BIGINT PRIMARY KEY,
  status   CHAR(1),
  del_flag CHAR(1)
);

CREATE TABLE IF NOT EXISTS o_shop (
  id          BIGINT PRIMARY KEY,
  status      CHAR(1),
  merchant_id BIGINT
);

CREATE TABLE IF NOT EXISTS erp_supplier (
  id        BIGINT PRIMARY KEY,
  disable   TINYINT,
  is_delete TINYINT
);

CREATE TABLE IF NOT EXISTS erp_warehouse (
  id     BIGINT PRIMARY KEY,
  status CHAR(1)
);

CREATE TABLE IF NOT EXISTS erp_business_user_binding (
  user_id      BIGINT      PRIMARY KEY,
  subject_type VARCHAR(16),
  subject_id   BIGINT,
  enabled      TINYINT,
  create_by    VARCHAR(64),
  create_time  TIMESTAMP,
  update_by    VARCHAR(64),
  update_time  TIMESTAMP
);
