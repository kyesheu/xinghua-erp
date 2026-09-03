INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT 'AI营销资料查询',9,196,'','F','1','0','ai:marketing:query','admin',NOW(),'按主体范围只读查询手动订单折扣规则'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=9)
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:marketing:query');
