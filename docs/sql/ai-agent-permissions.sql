-- 仅创建权限项，不给任何角色授权。执行前请检查目标数据库和菜单结构。
-- 可重复执行；已有相同权限标识时不创建。
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, visible, status, perms, create_by, create_time, remark)
SELECT 'AI库存查询', 9, 190, '', 'F', '1', '0', 'ai:inventory:query', 'admin', NOW(), '库存采购助手查询库存'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'ai:inventory:query');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, visible, status, perms, create_by, create_time, remark)
SELECT 'AI销售查询', 9, 191, '', 'F', '1', '0', 'ai:sales:query', 'admin', NOW(), '库存采购助手查询销售数量'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'ai:sales:query');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, visible, status, perms, create_by, create_time, remark)
SELECT 'AI采购查询', 9, 192, '', 'F', '1', '0', 'ai:purchase:query', 'admin', NOW(), '库存采购助手查询采购任务'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'ai:purchase:query');
-- 入出库权限仅登记菜单项，不自动授权给角色。
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '入库单查询',906,10,'','F','1','0','wms:stockin:query','admin',NOW(),'入库单列表与详情'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=906) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wms:stockin:query');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '入库操作',906,20,'','F','1','0','wms:stockin:operate','admin',NOW(),'创建和实际入库'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=906) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wms:stockin:operate');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '出库单查询',916,10,'','F','1','0','wms:stockout:query','admin',NOW(),'出库单列表与详情'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=916) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wms:stockout:query');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '出库操作',916,20,'','F','1','0','wms:stockout:operate','admin',NOW(),'创建和实际出库'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=916) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wms:stockout:operate');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '库存查询',9,193,'','F','1','0','wms:inventory:query','admin',NOW(),'主系统库存及批次查询'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=9) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wms:inventory:query');
-- 旧采购流程暂仅总部账号可使用；仅登记权限，不自动授予角色。
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '采购单查询',2120,10,'','F','1','0','purchase:order:query','admin',NOW(),'查询采购订单和物流'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=2120) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='purchase:order:query');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '采购单创建',2120,20,'','F','1','0','purchase:order:create','admin',NOW(),'创建待审核采购单'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=2120) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='purchase:order:create');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '采购单处理',2120,30,'','F','1','0','purchase:order:operate','admin',NOW(),'取消、发货、收货及生成入库任务'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=2120) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='purchase:order:operate');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '采购单审核',2120,40,'','F','1','0','purchase:order:audit','admin',NOW(),'审核待审核采购单'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=2120) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='purchase:order:audit');

-- Headquarters purchase carrier maintenance. Does not grant permissions to any role.
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '采购承运商维护',2120,50,'','F','1','0','purchase:logistics:manage','admin',NOW(),'新增、修改、启停和删除采购承运商'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=2120) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='purchase:logistics:manage');

-- Headquarters supplier archive maintenance. Does not grant permissions to any role.
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '供应商档案维护',2120,60,'','F','1','0','purchase:supplier:manage','admin',NOW(),'新增、修改、删除供应商档案；不自动授予角色'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=2120) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='purchase:supplier:manage');
