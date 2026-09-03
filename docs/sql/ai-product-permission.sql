INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT 'AI商品运营资料查询',9,195,'','F','1','0','ai:product:query','admin',NOW(),'按主体范围只读查询商品、库存及关联资料'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=9)
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:product:query');
