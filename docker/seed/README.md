# 演示数据

一份可重复导入的业务演示数据，用于本地演示与验收。导入后首页经营看板、商品、库存、采购、出入库、订单、发货、售后、账号数据范围都有数据。

## 内容

| 文件 | 作用 |
|---|---|
| `01-base.sql` | 商户 3、店铺 6、仓库 4（含 12 个库位）、供应商 8、类目 18、品牌 6、商品 40、SKU 83、库存 166、会员 100、账号 7、角色 4、图片台账 45 |
| `02-txn.sql` | 订单 285（近 60 天）、订单明细 492、发货 222、退款 40、售后 40、采购单 28、采购明细 76、入库 20、出库 26 |
| `03-fixup.sql` | 售后台账、采购发货记录、仓库状态、缺失的按钮级权限菜单 |
| `04-oms.sql` | 供应商产品 64（+SKU 129、报价 129）、店铺商品 86（+SKU 163）、采购承运商 14、店铺订单 240（+明细 368）、店铺售后 36、发货任务 110（+明细 154） |
| `gen_base.py` / `gen_txn.py` / `gen_oms.py` | 生成脚本，随机种子固定，重跑结果一致 |

四个 SQL 都必须按 `01 → 02 → 03 → 04` 顺序执行，脚本开头会先清空对应表，可反复导入。
`04-oms.sql` 依赖 `01`/`02` 已导入（复用其中的商户、店铺、供应商、商品、SKU 与平台订单），直接单独导入会因主数据缺失而变成空关联。

## 发货任务表说明

前端「发货记录」（`/api/erp-api/ship/record/record_list`）与「订单备货」
（`/api/erp-api/ship/stocking/stock_up_list`）读的都是 **`o_order_stocking`** /
`o_order_stocking_item`，而不是 `o_supplier_ship_order`。
`cn.xinghuaerp.inventory.entity.OOrderStocking` 上没有 `@TableName` 注解，
MyBatis-Plus 按类名推导表名得到 `o_order_stocking`；`o_supplier_ship_order` 目前是历史遗留空表。

两个页面的过滤条件不同，数据需同时满足：

| 页面 | 过滤条件 |
|---|---|
| 发货记录 | `type = 0`（前端固定传本地仓），`send_status` 可空 |
| 订单备货 | `type = 0` 且 `send_status = 2` |

因此 `04-oms.sql` 里本地仓任务中 `send_status = 2` 的 62 条两个页面都能看到，
另 26 条 `send_status` 为 0/1 只出现在发货记录页。

## 时间基准

`gen_oms.py` 里的 `NOW = 2026-11-13 14:30` 是生成数据的"当前时刻"，所有生成的时间戳都会 clamp 到它之前，
避免"未来订单 / 未来发货"在按时间倒序排序时浮到列表最前面。
若演示时间点变化，改这一个常量后重跑即可。

## 导入

```bash
cd <项目根目录>
for f in docker/seed/0*.sql; do
  docker exec -i xinghua-erp-mysql-1 mysql -uroot -pxinghua-erp-dev-only \
    --default-character-set=utf8mb4 xinghua-erp < "$f"
done
```

商品图与供应商营业执照已上传到 MinIO 的 `goods/2026-11/`、`supplier/license/2026-11/`，`01-base.sql` 里的图片地址指向 `http://localhost:19000/xinghua-erp/...`。

## 账号

密码各不相同，均为 BCrypt 存储。

| 账号 | 密码 | 角色 | 数据范围 |
|---|---|---|---|
| `admin` | `admin123` | 超级管理员 | 全部 |
| `hq_ops` | `Hq@2026ops` | 管理员 | 全部 |
| `m1_admin` | `Mer@2026m1` | 普通角色 | 杭州兴华（商户 1） |
| `shop1_ops` | `Shop@2026s1` | 店铺销售 | 兴华天猫旗舰店 |
| `wh_keeper` | `Wh@2026keep` | 仓管员 | 华东中心仓，只看库存与出入库 |
| `buyer01` | `Buy@2026po` | 采购员 | 全部（采购与商品） |
| `as_cs` | `Cs@2026svc` | 售后客服 | 优选抖音小店 |
| `fin01` | `Fin@2026ac` | 财务 | 全部（只读口径） |

## 重新生成

`gen_*.py` 需要连上本地 Docker 里的 MySQL（通过 `docker exec` 查询主数据），改完数据定义后执行：

```bash
python docker/seed/gen_base.py   # 输出 seed_base.sql
python docker/seed/gen_txn.py    # 输出 seed_txn.sql，依赖 01-base 已导入
python docker/seed/gen_oms.py    # 直接输出 docker/seed/04-oms.sql，依赖 01/02 已导入
```

`gen_base.py` / `gen_txn.py` 输出到当前工作目录，重命名后覆盖 `01-base.sql` / `02-txn.sql`；
`gen_oms.py` 直接写回 `docker/seed/04-oms.sql`，改完可原地重跑。
