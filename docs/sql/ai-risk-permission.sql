INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT 'AI风险售后资料查询',9,197,'','F','1','0','ai:risk:query','admin',NOW(),'按主体范围只读查询内部退款售后记录'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=9)
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ai:risk:query');
