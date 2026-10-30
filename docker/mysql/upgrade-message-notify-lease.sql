-- 已有数据库执行一次；新建数据库由 docs/sql/xinghua-erp.sql 创建。
--
-- 通知投递加租约令牌。原来 finishNotify 的围栏只有 notify_status=3（发送中），
-- 它只能表达“现在有人正在发”，不能表达“是不是我抢的”：租约超过 10 分钟补偿窗口被别的实例
-- 接管后状态仍然是 3，过期的持有者照样能结算，用它那份早已过时的结果覆盖接管者的真实结果。
-- 加上令牌后，接管时换新令牌，旧持有者的结算影响行数为 0。
--
-- 存量数据：升级前处于 notify_status=3 的行 notify_token 为 NULL，它们会被 10 分钟超时窗口
-- 重新捡起并通过 claimNotify 写入新令牌，无需刷数据。
ALTER TABLE sys_message ADD COLUMN notify_token varchar(36) NULL
    COMMENT '投递租约令牌：抢占时写入UUID，结算时按令牌匹配' AFTER notify_time;
