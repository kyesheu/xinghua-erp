-- 期初库存批次补齐：为每条有效库存生成两个批次，current_qty 合计等于库存数量。
-- 可重复导入：先清空批次表再整体重建；不修改 o_goods_inventory 的任何数量。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM o_goods_inventory_batch;

-- 批次 A：取库存数量的一半（向下取整）
INSERT INTO o_goods_inventory_batch
(id, inventory_id, batch_num, origin_qty, current_qty, pur_price, remark,
 sku_id, goods_id, sku_code, warehouse_id, merchant_id, shop_id, stock_status,
 inventory_mode, create_time, create_by, update_time, update_by)
SELECT i.id * 2, i.id,
       CONCAT('B', DATE_FORMAT(i.create_time, '%Y%m%d'), LPAD(i.id, 5, '0'), 'A'),
       GREATEST(i.quantity, 60), FLOOR(i.quantity / 2), IFNULL(s.pur_price, 0),
       '期初批次（前半量）',
       i.sku_id, i.goods_id, i.sku_code, i.warehouse_id,
       i.merchant_id, i.shop_id, i.stock_status,
       0, i.create_time, 'system', i.create_time, 'system'
FROM o_goods_inventory i
LEFT JOIN o_goods_sku s ON s.id = i.sku_id
WHERE COALESCE(i.is_delete, 0) = 0;

-- 批次 B：承接剩余数量，保证两个批次 current_qty 之和精确等于库存数量
INSERT INTO o_goods_inventory_batch
(id, inventory_id, batch_num, origin_qty, current_qty, pur_price, remark,
 sku_id, goods_id, sku_code, warehouse_id, merchant_id, shop_id, stock_status,
 inventory_mode, create_time, create_by, update_time, update_by)
SELECT i.id * 2 + 1, i.id,
       CONCAT('B', DATE_FORMAT(i.create_time, '%Y%m%d'), LPAD(i.id, 5, '0'), 'B'),
       GREATEST(i.quantity, 60) - FLOOR(i.quantity / 2),
       i.quantity - FLOOR(i.quantity / 2), IFNULL(s.pur_price, 0),
       '期初批次（后半量）',
       i.sku_id, i.goods_id, i.sku_code, i.warehouse_id,
       i.merchant_id, i.shop_id, i.stock_status,
       0, i.create_time, 'system', i.create_time, 'system'
FROM o_goods_inventory i
LEFT JOIN o_goods_sku s ON s.id = i.sku_id
WHERE COALESCE(i.is_delete, 0) = 0;

SET FOREIGN_KEY_CHECKS = 1;
