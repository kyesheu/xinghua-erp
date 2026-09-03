-- 增量迁移：仅创建账号与业务主体绑定表，不自动绑定历史账号。
-- 由管理员在确认目标数据库和账号绑定方案后执行；不修改用户、商户或订单数据。
CREATE TABLE IF NOT EXISTS `erp_business_user_binding` (
  `user_id` BIGINT NOT NULL COMMENT '系统用户ID，一名账号一个业务主体',
  `subject_type` VARCHAR(16) NOT NULL COMMENT 'MERCHANT、SHOP、SUPPLIER、WAREHOUSE',
  `subject_id` BIGINT NOT NULL COMMENT '业务主体ID',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用，0停用',
  `create_by` VARCHAR(64) NULL,
  `create_time` DATETIME NULL,
  `update_by` VARCHAR(64) NULL,
  `update_time` DATETIME NULL,
  PRIMARY KEY (`user_id`),
  KEY `idx_business_subject` (`subject_type`, `subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统账号业务主体绑定';
