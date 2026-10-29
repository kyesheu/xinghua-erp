# 本地演示数据

本目录提供用于熟悉页面和业务流程的示例商户、店铺、商品、采购、订单与售后数据。它们是可选内容，不属于正式业务数据，也不会随基础库初始化自动导入。

## 文件与顺序

| 文件 | 内容 |
| --- | --- |
| `01-base.sql` | 账号、商户、店铺、供应商、仓库、商品、SKU 及基础库存 |
| `02-txn.sql` | 采购、出入库、订单、售后及财务单据 |
| `03-supplement.sql` | 补充业务资料及页面所需配置 |
| `04-oms.sql` | 店铺商品、平台订单、售后与发货任务 |

须先完成[基础数据库初始化](../README.md)，再按 `01 → 02 → 03 → 04` 导入。脚本包含清理和覆盖示例数据的语句，只能用于可重置的本地演示库；不要导入已有真实业务数据的数据库。

在项目根目录可使用 PowerShell 逐文件导入：

```powershell
$seedFiles = @('01-base.sql', '02-txn.sql', '03-supplement.sql', '04-oms.sql')
foreach ($seedFile in $seedFiles) {
    Get-Content -Raw -Encoding utf8 (Join-Path 'docker/seed' $seedFile) |
        docker exec -i xinghua-erp-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 --database=xinghua-erp'
    if ($LASTEXITCODE -ne 0) { throw "导入失败：$seedFile" }
}
```

## 使用入口

导入后从商户、店铺、商品、采购和订单列表了解数据关联，再按[业务流程](../../docs/采购销售库存业务流程.md)操作。示例图片地址需要与本机 MinIO 地址及实际对象一致；导入 SQL 不等于上传图片。

管理员账号用于配置角色和业务归属。示例还包含总部运营、商户、店铺、仓库、采购、售后和财务等账号，实际账号状态和可用权限以用户管理页面为准；需要交付账号时由管理员设置密码。

发货记录和订单备货使用 `o_order_stocking` 及其明细。生成发货任务不代表已经扣减库存，应继续完成实际出库。
