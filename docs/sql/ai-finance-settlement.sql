-- 仅登记订单结算执行权限，不自动授予任何角色；不创建业务数据。
-- 结算执行接口为 POST /api/erp-api/fms/settlement/auto-run（显式订单ID，单次最多200笔），
-- 金额计算全部由 FmsOrderSettlementService 完成；执行属于写操作，须由具备权限的人员人工发起并核对结果。
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time,remark)
SELECT '订单结算执行',9,199,'','F','1','0','fms:settlement:execute','admin',NOW(),'对显式订单执行自动结算（成本取出库批次快照、运费取发货记录、费用取已审核费用单）；不代表财务资料查询权限'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=9)
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='fms:settlement:execute');
