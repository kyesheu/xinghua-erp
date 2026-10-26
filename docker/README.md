# docker/ 目录说明

所有容器编排与配置集中在本目录，**改配置直接改本目录下的文件，重启对应容器即生效**。

## 文件结构

| 路径 | 作用 |
|---|---|
| `docker-compose.yml` | 本地开发环境编排（MySQL / Redis / MinIO / RabbitMQ，backend / frontend / nginx 为注释模板） |
| `mysql/conf.d/` | **映射到 MySQL 容器 `/etc/mysql/conf.d`**：把自定义 `my.cnf` 放进来即生效（重启容器读取） |
| `nginx/nginx.conf` | **映射到 nginx 容器 `/etc/nginx/nginx.conf`**：前端生产静态部署配置（含 `/prod-api/` 反代与 SSE 支持） |

## 常用命令（在本目录执行）

```bash
docker compose up -d          # 启动 MySQL/Redis/MinIO/RabbitMQ（首次会自动初始化数据库）
docker compose ps             # 查看状态与健康检查
docker compose down           # 停止（保留数据卷）
docker compose down -v        # 停止并清空数据卷（下次启动重新初始化数据库）
```

## 数据库初始化（重要）

MySQL 只在**数据卷为空**时执行初始化，脚本按容器内文件名顺序运行：

| 顺序 | 宿主文件 | 容器内路径 | 作用 |
|---|---|---|---|
| 01 | `../docs/sql/xinghua-erp.sql` | `01-xinghua-erp.sql` | 主库结构与基础数据（145 张表） |
| 02 | `../docs/sql/ai-inventory-review.sql` | `02-ai-inventory-review.sql` | 库存补货人机审核任务表 |
| 03 | `../docs/sql/ai-purchase-draft.sql` | `03-ai-purchase-draft.sql` | Agent 采购草稿幂等记录表 |
| 04 | `../docs/sql/business-user-binding-migration.sql` | `04-business-user-binding.sql` | 账号与业务主体绑定表 |
| 05–10 | `../docs/sql/ai-*-permission.sql`、`ai-finance-settlement.sql` | 对应编号 | AI 各查询权限与结算执行权限的菜单项（幂等，不授予任何角色） |

注意两点：

- **一条 SQL 报错会导致其后的语句整段不执行**（mysql 客户端批量模式遇错即停）。修改初始化脚本后，确保每条语句都能执行成功，尤其注意字段长度与种子数据是否匹配。
- 健康检查不只 `SELECT 1`，还会校验 `sys_message`、`sys_user`、`ai_config`、`ai_inventory_review_task` 四张位于脚本末尾的表是否齐全。若初始化失败，MySQL 容器会停在 `unhealthy`，`docker compose ps` 能直接看出来。

排查初始化失败：

```bash
docker logs xinghua-erp-mysql-1 2>&1 | grep -E "running /docker-entrypoint-initdb.d|ERROR"
docker compose down -v && docker compose up -d   # 改过 SQL 必须清卷重建，否则不会重新导入
```

物流公司基础数据（2373 条）已在主 SQL 内，`docs/sql/erp_logistics_company-data.sql` 仅用于单独覆盖导入，不参与初始化。

### 已有数据卷不会重跑脚本

初始化脚本只对空数据卷生效。表结构或 MinIO 策略改过之后，老环境要手动补一次：

```sql
-- 例：sys_oss 新增业务分类与文件大小
ALTER TABLE sys_oss
  ADD COLUMN category varchar(50) DEFAULT '' COMMENT '业务分类' AFTER bucket,
  ADD COLUMN file_size bigint DEFAULT 0 COMMENT '文件大小（字节）' AFTER category;
```

```bash
docker compose up -d --force-recreate minio-init   # 重新应用存储桶匿名读策略
```

## 端口约定（仅监听本机 127.0.0.1）

| 服务 | 宿主端口 | 容器端口 |
|---|---|---|
| MySQL | 13370 | 3306（库名 `xinghua-erp`，root 密码由环境变量 `MYSQL_ROOT_PASSWORD` 注入） |
| Redis | 16379 | 6379 |
| MinIO API / 控制台 | 19000 / 19001 | 9000 / 9001（桶 `xinghua-erp`，账号由 `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` 注入） |
| RabbitMQ AMQP / 管理台 | 56720 / 15672 | 5672 / 15672（账号由 `SPRING_RABBITMQ_USERNAME` / `SPRING_RABBITMQ_PASSWORD` 注入） |
| nginx（可选，取消注释后） | 80 | 80 |

## 凭据配置（重要）

本目录**不保存任何明文密码**，启动前请先在当前 shell 注入环境变量，未设置时使用仅供本地开发的占位默认值：

```bash
export MYSQL_ROOT_PASSWORD=xinghua-erp-dev-only   # 与后端 DB_PASSWORD 保持一致
export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin
```

## 可选服务（取消 docker-compose.yml 中对应注释后使用）

- **backend**：容器化后端，需先在 `erp-backend/` 执行 `mvn -pl erp-api -am clean package -DskipTests` 产出 jar。
- **frontend**：容器化前端开发服务（Vite 热更新），访问 http://localhost:88。
- **nginx**：前端生产静态部署，需先在 `erp-frontend/` 执行 `npm run build` 产出 `dist/`，访问 http://localhost:80。
