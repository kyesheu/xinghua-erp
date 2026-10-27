SET NAMES utf8mb4;
UPDATE o_refund SET process_type = NULL;
DELETE FROM o_refund_after_sale;
INSERT INTO o_refund_after_sale
(id, type, shop_id, shop_type, refund_id, refund_num, order_num, sub_order_num, o_order_id, o_order_item_id,
 sku_id, quantity, title, img, sku_info, sku_code, o_goods_id, o_goods_sku_id, has_goods_send, send_logistics_code,
 return_info, return_logistics_code, return_logistics_company, receiver_name, receiver_tel, receiver_province,
 receiver_city, receiver_town, receiver_address, status, remark, create_time, create_by, merchant_id,
 supplier_id, send_warehouse_id, send_warehouse_type, send_warehouse_name, return_warehouse_id,
 return_warehouse_name, return_type, reissue_type, result)
SELECT a.id, a.type, a.shop_id, a.shop_type, a.refund_id, a.refund_num, a.order_num, a.sub_order_num,
 CAST(a.o_order_id AS UNSIGNED), CAST(a.o_order_item_id AS UNSIGNED), a.sku_id, a.quantity, a.title, a.img,
 a.sku_info, a.sku_code, a.o_goods_id, a.o_goods_sku_id, a.has_goods_send,
 CONCAT('SF', LPAD(a.id * 137, 12, '0')),
 a.return_info, a.return_logistics_code, a.return_logistics_company, a.receiver_name, a.receiver_tel,
 a.receiver_province, a.receiver_city, a.receiver_town, a.receiver_address, a.status, a.remark,
 a.create_time, a.create_by, o.merchant_id, 1, 1, '1', '华东中心仓', 0, '华东中心仓',
 1, 0, '客户已签收，退款已完成'
FROM o_after_sale a JOIN o_order o ON o.id = CAST(a.o_order_id AS UNSIGNED);
SET NAMES utf8mb4;
DELETE FROM erp_purchase_order_ship;
INSERT INTO erp_purchase_order_ship
(id, supplier_id, order_id, ship_company, ship_num, freight, ship_time, receipt_time, create_by, create_time,
 status, remark, stock_in_time, stock_in_count, order_date, order_num, order_spec_unit, order_goods_unit,
 order_spec_unit_total, merchant_id, warehouse_id, warehouse_type, warehouse_name, shop_id)
SELECT p.id, p.supplier_id, p.id, '顺丰速运', CONCAT('SF', LPAD(p.id * 7919, 12, '0')), 20.00,
       p.supplier_delivery_time, p.received_time, 'buyer01', p.create_time,
       CASE WHEN p.status = 2 THEN 2 ELSE 1 END, '供应商发货记录',
       p.stock_in_time, IFNULL(p.status = 2, 0), p.order_date, p.order_num, 0, 0, 0,
       p.merchant_id, p.warehouse_id, p.warehouse_type, p.warehouse_name, 0
FROM erp_purchase_order p WHERE p.status IN (1, 2);
SET NAMES utf8mb4;
UPDATE erp_warehouse SET status = '1';
DELETE FROM erp_warehouse_position;
INSERT INTO erp_warehouse_position (id, warehouse_id, number, name, parent_id, depth, address, remark, is_delete, create_by, create_time, merchant_id)
VALUES
(1,1,'A','A 区',0,1,'1F-东侧','常温存储区',0,'admin','2026-06-06 09:00:00',1),
(2,1,'B','B 区',0,1,'1F-西侧','小件存储区',0,'admin','2026-06-06 09:05:00',1),
(3,1,'A-01','A 区 01 货位',1,2,'1F-东侧-01','',0,'admin','2026-06-06 09:10:00',1),
(4,1,'A-02','A 区 02 货位',1,2,'1F-东侧-02','',0,'admin','2026-06-06 09:11:00',1),
(5,1,'B-01','B 区 01 货位',2,2,'1F-西侧-01','',0,'admin','2026-06-06 09:12:00',1),
(6,2,'A','A 区',0,1,'1F','前置仓存储',0,'admin','2026-06-06 10:00:00',2),
(7,2,'A-01','A 区 01 货位',6,2,'1F-01','',0,'admin','2026-06-06 10:05:00',2),
(8,3,'A','A 区',0,1,'3F','华南主存储区',0,'admin','2026-06-06 11:00:00',3),
(9,3,'A-01','A 区 01 货位',8,2,'3F-01','',0,'admin','2026-06-06 11:05:00',3),
(10,3,'B','B 区',0,1,'3F-北侧','大件存储区',0,'admin','2026-06-06 11:10:00',3),
(11,4,'A','A 区',0,1,'1F','周转存储',0,'admin','2026-06-06 12:00:00',1),
(12,4,'A-01','A 区 01 货位',11,2,'1F-01','',0,'admin','2026-06-06 12:05:00',1);
SET NAMES utf8mb4;
DELETE FROM sys_role_menu WHERE role_id IN (2,3);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.menu_id FROM sys_menu m
WHERE m.menu_id IN (1,2,3,4,7,8,9) OR m.parent_id IN (1,2,3,4,7,8,9)
   OR m.parent_id IN (SELECT x.menu_id FROM (SELECT menu_id, parent_id FROM sys_menu) x WHERE x.parent_id IN (1,2,3,4,7,8,9));
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.menu_id FROM sys_menu m
WHERE m.menu_id IN (1,2,4,8) OR m.parent_id IN (1,2,4,8)
   OR m.parent_id IN (SELECT x.menu_id FROM (SELECT menu_id, parent_id FROM sys_menu) x WHERE x.parent_id IN (1,2,4,8));
SET NAMES utf8mb4;
DELETE FROM sys_menu WHERE menu_id BETWEEN 2300 AND 2360;
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
(2301,'订单列表',100,1,'','',NULL,1,0,'F','0','0','shop:order:list','#','admin','2026-06-25 10:00:00','按钮权限'),
(2302,'订单详情',100,2,'','',NULL,1,0,'F','0','0','shop:order:query','#','admin','2026-06-25 10:00:00','按钮权限'),
(2303,'订单编辑',100,3,'','',NULL,1,0,'F','0','0','shop:order:edit','#','admin','2026-06-25 10:00:00','按钮权限'),
(2304,'商品列表',401,1,'','',NULL,1,0,'F','0','0','goods:goods:list','#','admin','2026-06-25 10:00:00','按钮权限'),
(2305,'商品详情',401,2,'','',NULL,1,0,'F','0','0','goods:goods:query','#','admin','2026-06-25 10:00:00','按钮权限'),
(2306,'商品新增',401,3,'','',NULL,1,0,'F','0','0','goods:goods:add','#','admin','2026-06-25 10:00:00','按钮权限'),
(2307,'商品修改',401,4,'','',NULL,1,0,'F','0','0','goods:goods:edit','#','admin','2026-06-25 10:00:00','按钮权限'),
(2308,'商品删除',401,5,'','',NULL,1,0,'F','0','0','goods:goods:remove','#','admin','2026-06-25 10:00:00','按钮权限'),
(2309,'店铺列表',308,1,'','',NULL,1,0,'F','0','0','shop:shop:list','#','admin','2026-06-25 10:00:00','按钮权限'),
(2310,'店铺详情',308,2,'','',NULL,1,0,'F','0','0','shop:shop:query','#','admin','2026-06-25 10:00:00','按钮权限'),
(2311,'店铺新增',308,3,'','',NULL,1,0,'F','0','0','shop:shop:add','#','admin','2026-06-25 10:00:00','按钮权限'),
(2312,'店铺修改',308,4,'','',NULL,1,0,'F','0','0','shop:shop:edit','#','admin','2026-06-25 10:00:00','按钮权限'),
(2313,'店铺删除',308,5,'','',NULL,1,0,'F','0','0','shop:shop:remove','#','admin','2026-06-25 10:00:00','按钮权限'),
(2314,'用户列表',516,1,'','',NULL,1,0,'F','0','0','system:user:list','#','admin','2026-06-25 10:00:00','按钮权限'),
(2315,'用户详情',516,2,'','',NULL,1,0,'F','0','0','system:user:query','#','admin','2026-06-25 10:00:00','按钮权限'),
(2316,'用户新增',516,3,'','',NULL,1,0,'F','0','0','system:user:add','#','admin','2026-06-25 10:00:00','按钮权限'),
(2317,'用户修改',516,4,'','',NULL,1,0,'F','0','0','system:user:edit','#','admin','2026-06-25 10:00:00','按钮权限'),
(2318,'用户删除',516,5,'','',NULL,1,0,'F','0','0','system:user:remove','#','admin','2026-06-25 10:00:00','按钮权限'),
(2319,'重置密码',516,6,'','',NULL,1,0,'F','0','0','system:user:resetPwd','#','admin','2026-06-25 10:00:00','按钮权限'),
(2320,'部门查询',516,7,'','',NULL,1,0,'F','0','0','system:dept:list','#','admin','2026-06-25 10:00:00','按钮权限'),
(2321,'部门详情',516,8,'','',NULL,1,0,'F','0','0','system:dept:query','#','admin','2026-06-25 10:00:00','按钮权限'),
(2322,'角色查询',2090,1,'','',NULL,1,0,'F','0','0','system:role:list','#','admin','2026-06-25 10:00:00','按钮权限'),
(2323,'角色详情',2090,2,'','',NULL,1,0,'F','0','0','system:role:query','#','admin','2026-06-25 10:00:00','按钮权限'),
(2324,'菜单查询',517,1,'','',NULL,1,0,'F','0','0','system:menu:list','#','admin','2026-06-25 10:00:00','按钮权限'),
(2325,'菜单详情',517,2,'','',NULL,1,0,'F','0','0','system:menu:query','#','admin','2026-06-25 10:00:00','按钮权限'),
(2326,'菜单新增',517,3,'','',NULL,1,0,'F','0','0','system:menu:add','#','admin','2026-06-25 10:00:00','按钮权限'),
(2327,'菜单修改',517,4,'','',NULL,1,0,'F','0','0','system:menu:edit','#','admin','2026-06-25 10:00:00','按钮权限'),
(2328,'菜单删除',517,5,'','',NULL,1,0,'F','0','0','system:menu:remove','#','admin','2026-06-25 10:00:00','按钮权限'),
(2329,'字典查询',579,1,'','',NULL,1,0,'F','0','0','system:dict:list','#','admin','2026-06-25 10:00:00','按钮权限'),
(2330,'字典详情',579,2,'','',NULL,1,0,'F','0','0','system:dict:query','#','admin','2026-06-25 10:00:00','按钮权限'),
(2331,'字典新增',579,3,'','',NULL,1,0,'F','0','0','system:dict:add','#','admin','2026-06-25 10:00:00','按钮权限'),
(2332,'字典修改',579,4,'','',NULL,1,0,'F','0','0','system:dict:edit','#','admin','2026-06-25 10:00:00','按钮权限'),
(2333,'字典删除',579,5,'','',NULL,1,0,'F','0','0','system:dict:remove','#','admin','2026-06-25 10:00:00','按钮权限'),
(2334,'退货列表',204,1,'','',NULL,1,0,'F','0','0','api:returned:list','#','admin','2026-06-25 10:00:00','按钮权限'),
(2335,'退货详情',204,2,'','',NULL,1,0,'F','0','0','api:returned:query','#','admin','2026-06-25 10:00:00','按钮权限');

DELETE FROM sys_role_menu WHERE role_id IN (2,3,5,6) AND menu_id BETWEEN 2300 AND 2360;
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 2, menu_id FROM sys_menu WHERE menu_id BETWEEN 2300 AND 2360;
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 3, menu_id FROM sys_menu WHERE perms IN ('shop:order:list','shop:order:query','goods:goods:list','goods:goods:query','api:returned:list','api:returned:query');
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 5, menu_id FROM sys_menu WHERE perms IN ('goods:goods:list','goods:goods:query','goods:goods:add','goods:goods:edit');
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 6, menu_id FROM sys_menu WHERE perms IN ('shop:order:list','shop:order:query','api:returned:list','api:returned:query');
