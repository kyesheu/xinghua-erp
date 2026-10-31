-- 已有数据库执行一次；新建数据库由 docs/sql/xinghua-erp.sql 创建。
--
-- 下单锁库：ShopOrderServiceImpl.createOrder 里原本只有一个空的 if 块
-- （`if (order.getIsLock()==1) { }`），前端传「是否锁库」后端收下就丢，
-- OGoodsInventoryService.lockStock() 零调用方，并发下单会超卖。
--
-- 锁定数量落在 o_goods_inventory.locked_quantity（原子条件更新，可用量不足时影响行数为 0），
-- 本表只记录「哪个订单占了哪条库存多少」，取消订单或锁过期时据此精确回补。
--
-- 注：实体 GoodsInventoryLock 映射的 o_goods_inventory_lock 在任何建表脚本里都不存在，
-- 那条链路（调拨锁库）本来就是坏的，本次不复用，另起一张订单专用的表。
CREATE TABLE IF NOT EXISTS `o_shop_order_stock_lock`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '销售订单ID（shop_order.id）',
  `inventory_id` bigint NOT NULL COMMENT '库存行ID（o_goods_inventory.id）',
  `sku_id` bigint NOT NULL COMMENT '商品库SKU ID',
  `lock_quantity` int NOT NULL COMMENT '锁定数量',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1-锁定中 2-已核销 3-已释放',
  `expire_time` datetime NOT NULL COMMENT '锁定过期时间，到点由定时任务释放',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_lock_order`(`order_id`, `status`) USING BTREE,
  INDEX `idx_lock_expire`(`status`, `expire_time`) USING BTREE,
  INDEX `idx_lock_inventory`(`inventory_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售订单库存锁定记录' ROW_FORMAT = DYNAMIC;
