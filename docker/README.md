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
docker compose up -d          # 启动 MySQL/Redis/MinIO/RabbitMQ（首次会自动导入 ../docs/sql/xinghua-erp.sql）
docker compose ps             # 查看状态与健康检查
docker compose down           # 停止（保留数据卷）
docker compose down -v        # 停止并清空数据卷（下次启动重新初始化数据库）
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
