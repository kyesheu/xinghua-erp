# 容器环境说明

本目录集中保存容器编排、MySQL 配置、nginx 代理配置和可选演示数据。完整应用启动顺序见[部署指南](../docs/部署指南.md)。

## 服务与文件

`docker-compose.yml` 默认启动 MySQL、Redis、MinIO、MinIO 初始化任务和 RabbitMQ。Java、前端与 nginx 的注释段属于部署配置示例，不会自动启动应用；启用前需按实际构建产物、Java 21 和网络地址调整。

| 路径 | 用途 |
| --- | --- |
| `docker-compose.yml` | 服务、端口、数据卷和初始化 SQL 挂载 |
| `mysql/conf.d/` | 映射到 MySQL 的自定义配置 |
| `mysql/upgrade-order-reliability.sql` | 已有数据库的一次性订单通知结构升级 |
| `nginx/nginx.conf` | 静态页面、API 反向代理及 SSE 配置 |
| `seed/` | 可选的本地演示数据 |

## 启停

在本目录执行：

```powershell
docker compose up -d
docker compose ps
docker compose logs mysql
docker compose down
```

`down` 保留数据卷。删除数据卷会同时删除对应服务的数据，不用于日常升级。

## 数据库初始化

MySQL 仅在数据目录为空时执行初始化。脚本来自 `docs/sql/`，顺序由 Compose 的挂载目标编号决定：

| 编号 | 内容 |
| --- | --- |
| 01 | `xinghua-erp.sql`：主表结构、基础数据及订单 Outbox |
| 02–03 | AI 库存审核任务、采购草稿幂等记录 |
| 04 | 用户与业务主体绑定 |
| 05–10 | AI 功能权限及财务结算权限登记 |

主 SQL 含删除重建表语句，不可重复导入已有业务库。权限登记不等于角色授权，需在管理页面按岗位授权。已有数据卷不会因文件变更而重新执行初始化，应检查实际结构并执行对应的增量 SQL。

MySQL 健康检查校验基础连接及关键表。初始化失败时先查看容器日志，定位失败语句；修复结构或数据问题后再启动应用。

## 访问与配置

| 服务 | 本机端口 |
| --- | --- |
| MySQL | 13370 |
| Redis | 16379 |
| MinIO API / 控制台 | 19000 / 19001 |
| RabbitMQ AMQP / 管理台 | 56720 / 15672 |

端口默认绑定 `127.0.0.1`。数据库密码通过 `MYSQL_ROOT_PASSWORD` 注入，并与后端 `DB_PASSWORD` 保持一致。MinIO 和 RabbitMQ 凭据按 Compose 中的环境变量配置；默认值仅供本机环境。

MinIO 初始化任务创建 `xinghua-erp` 桶及配置的图片读取策略。重新应用策略可执行 `docker compose up -d --force-recreate minio-init`，文件数据仍保存在数据卷中。修改 MySQL 配置后需重启 MySQL；nginx 配置修改后需重新加载或重启对应服务。
