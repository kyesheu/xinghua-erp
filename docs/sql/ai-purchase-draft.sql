-- Agent 采购草稿的幂等记录；不自动创建采购单，也不授予权限。
CREATE TABLE IF NOT EXISTS ai_purchase_draft_request (
  request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  quantity BIGINT NOT NULL,
  unit_price DECIMAL(8,2) NOT NULL,
  purchase_order_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (request_key)
) ENGINE=InnoDB;
