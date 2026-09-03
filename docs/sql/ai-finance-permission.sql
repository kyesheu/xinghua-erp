-- 仅登记独立的财务资料查询权限，不自动授予任何角色；不创建业务数据。
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT 'AI财务资料查询',9,194,'','F','1','0','ai:finance:query','admin',NOW(),'按授权主体查询支付、退款与已登记财务资料；不代表利润计算权限或自动财务操作'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=9)
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:finance:query');
