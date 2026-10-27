# 演示数据

一份可重复导入的业务演示数据，用于本地演示与验收。导入后首页经营看板、商品、库存、采购、出入库、订单、发货、售后、账号数据范围都有数据。

## 内容

| 文件 | 作用 |
|---|---|
| `01-base.sql` | 商户 3、店铺 6、仓库 4（含 12 个库位）、供应商 8、类目 18、品牌 6、商品 40、SKU 83、库存 166、会员 100、账号 7、角色 4、图片台账 45 |
| `02-txn.sql` | 订单 285（近 60 天）、订单明细 492、发货 222、退款 40、售后 40、采购单 28、采购明细 76、入库 20、出库 26 |
| `03-fixup.sql` | 售后台账、采购发货记录、仓库状态、缺失的按钮级权限菜单 |
| `gen_base.py` / `gen_txn.py` | 生成脚本，随机种子固定，重跑结果一致 |

三个 SQL 都必须按 `01 → 02 → 03` 顺序执行，脚本开头会先清空对应表，可反复导入。

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
```

生成脚本输出到当前工作目录，重命名后覆盖 `01-base.sql` / `02-txn.sql` 即可。
