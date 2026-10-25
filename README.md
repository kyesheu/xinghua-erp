# 电商 ERP 系统（多商户进销存平台）

面向多商户电商企业的进销存管理系统，对接淘宝、拼多多、抖音、京东、快手、微信视频号、小红书 7 个国内电商平台的开放接口，覆盖商品、订单、库存、采购、发货全链路，内置基于 LangGraph 的 AI 智能经营分析。

## 核心功能

- **商品管理**：SPU/SKU 双层模型、类目属性体系、店铺商品与内部 SKU 映射归集
- **订单管理**：多平台订单/售后定时拉取、幂等入库、审核流转、打单发货（手工/打印/电子面单/供应商代发/云仓）；订单转入后经 RabbitMQ 异步通知下游
- **库存管理**：多仓库多库位、出入库单据流、订单占用锁定防超卖、出库批次成本快照
- **采购管理**：采购单、供应商与报价、采购入库任务核对
- **财务**：结算单利润核算（销售额 − 批次成本 − 运费 − 费用单）、财务流水、平台账单导入
- **权限体系**：RBAC 接口权限 + 总部/商户/店铺三级主体数据范围，服务端 SQL 层过滤防横向越权
- **AI 智能体**：AI 助手对话（模型自主调用工具取数）、自然语言经营分析（多主题并行）、SKU 表现排行、订单级亏损穿透、多店对比、明细导出、库存补货人工审核闭环

完整清单见 [功能说明](docs/功能说明.md)。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4.1、MyBatis-Plus、MySQL 8、Redis、RabbitMQ、JWT、MinIO |
| AI 服务 | Python 3.12、FastAPI、LangGraph、LangChain、Pydantic |
| 前端 | Vue 3、Vite、Element Plus |
| 架构 | 前后端分离的模块化单体（Modular Monolith）：后端按业务领域拆分为 10 个 Maven 模块，`erp-api` 唯一组装层 |

## 项目结构

```
├── erp-backend/                # Java 后端（模块化单体，按业务领域拆分为 10 个 Maven 模块）
│   ├── erp-common/             #   技术基座：统一结果/异常/分页/缓存/通用配置 + Jackson 3 JSON 工具
│   ├── erp-system/             #   系统与主数据：用户/角色/权限/商户/店铺、认证授权
│   ├── erp-product/            #   商品：SPU/SKU/类目/品牌/店铺商品/供应商报价
│   ├── erp-channel/            #   渠道接入：平台开放 SDK + 平台授权（OAuth/Token）
│   ├── erp-order/              #   订单：同步/处理/售后/退款
│   ├── erp-fulfillment/        #   履约：发货/物流/电子面单/供应商代发
│   ├── erp-inventory/          #   库存：仓库/库存/出入库/备货/锁定/调拨
│   ├── erp-purchase/           #   采购：采购单/采购入库/供应商
│   ├── erp-finance/            #   财务与报表：成本/结算/费用/账本 + 仪表盘/经营统计
│   └── erp-api/                #   启动与组装：全局配置、REST、MQ、定时任务、AI 工具网关
├── ai-service/                 # Python AI 服务（FastAPI + LangGraph）
│   ├── app/workflows/          #   LangGraph 工作流：对话与分析编排、多主题并行分析、库存补货人机审核等
│   ├── app/tools/              #   模型可用工具：query_erp 经 HTTP 回调 Java 取数
│   ├── app/core/               #   配置加载
│   ├── app/schemas/            #   业务数据模型（Pydantic）
│   ├── app/rules/              #   库存/补货规则
│   └── tests/                  #   Mock LLM 离线回归测试
├── erp-frontend/               # Vue 3 前端
│   ├── src/views/              #   页面，按业务域分目录（goods/order/inventory/shipping/ai/system…）
│   ├── src/store/modules/permission.ts   # 动态路由：后端菜单 → 视图组件映射
│   └── src/api/                #   接口封装，按业务域分目录
├── docker/                     # 容器编排与配置映射（compose、MySQL 初始化、nginx）
└── docs/                       # 说明文档与数据库 SQL
```

## 快速开始

以下命令均在仓库根目录执行（括号包裹是为了切目录后自动回到根目录）。

```bash
# 1. 基础设施：MySQL / Redis / MinIO / RabbitMQ
#    MySQL 首次启动会自动执行 docs/sql 下的 10 个初始化脚本，无需手工导库
(cd docker && docker compose up -d)
docker compose -f docker/docker-compose.yml ps     # 等 mysql 显示 healthy 再继续

# 2. 后端（端口 6149）
mvn -f erp-backend/pom.xml clean install
java -jar erp-backend/erp-api/target/erp-api-4.1.0.jar

# 3. 前端（http://localhost:88）
(cd erp-frontend && npm install && npm run dev)

# 4. AI 服务（端口 8000，未配置模型也能运行）
(cd ai-service && python -m venv .venv && .venv/Scripts/pip install -e .)
(cd ai-service && .venv/Scripts/uvicorn app.main:app --port 8000)
```

详细步骤与配置说明见 [部署指南](docs/部署指南.md)。

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [功能说明](docs/功能说明.md) | 全量功能清单与技术栈 |
| [AI 智能体说明](docs/AI智能体说明.md) | AI 模块设计、功能与配置 |
| [系统架构](docs/系统架构.md) | 模块、请求与数据关系 |
| [部署指南](docs/部署指南.md) | 本地部署完整步骤 |
| [docker/](docker/README.md) | 容器编排与配置映射（compose、MySQL/nginx 配置） |
| [业务账号权限与数据范围](docs/业务账号权限与数据范围.md) | 多级主体权限机制 |
| [采购销售库存业务流程](docs/采购销售库存业务流程.md) | 业务操作流程 |
| [消息与外部通知设计](docs/消息与外部通知设计.md) | 消息通知与 MQ 机制 |

## 测试

```bash
# 后端（在 erp-backend/ 下）
mvn -o -pl erp-api -am clean test

# AI 服务（在 ai-service/ 下）
.venv/Scripts/python.exe -m pytest

# 前端类型检查（在 erp-frontend/ 下）
npx vue-tsc --noEmit -p tsconfig.app.json
```

## 本地配置

仓库内**不包含任何明文凭据**：数据库与对象存储凭据通过环境变量注入，大模型密钥由页面维护。

### 大模型配置（页面维护）

登录后进入「AI 智能 > 模型配置」，填写 API 地址、API 密钥、模型名称，启用并设为默认即可。
密钥保存在 `ai_config` 表，Java 侧的对话/分析能力与 Python 服务的工作流都从这一处读取
（后端调用 AI 服务时经请求头透传模型地址、密钥与模型名），因此**不需要**再设置 `DEEPSEEK_API_KEY`，
也**不需要**把密钥写进 `ai-service/config.local.json`。保存后可点行内「测试」按钮实测连通性。

支持任何兼容 OpenAI 协议的服务（DeepSeek、阿里云百炼、OpenAI、Ollama 等），多个配置可共存并随时切换默认。

### 本地配置文件（application-dev.yml）

本机相关的值（数据库密码、Redis 密码、对象存储账号）建议写在
`erp-backend/erp-api/src/main/resources/application-dev.yml`：把同目录的 `application-dev.yml.example`
复制一份改名即可。该文件已在 `.gitignore` 中排除，**不会被提交**；`.example` 只作模板随仓库分发。

`application.yml` 的 `spring.profiles.active` 默认为 `dev`（可用环境变量 `SPRING_PROFILES_ACTIVE` 覆盖），
存在 `application-dev.yml` 时会自动叠加，同名键优先级更高。全新克隆的仓库没有这个文件也能正常启动。

### 环境变量

首次运行前请按需设置：

| 环境变量 | 用途 | 说明 |
| --- | --- | --- |
| `DB_PASSWORD` | MySQL 连接密码 | 有开发默认值 `xinghua-erp-dev-only`（与 compose 默认一致），生产必须通过环境变量覆盖 |
| `MYSQL_ROOT_PASSWORD` | 容器 MySQL root 密码 | 启动 `docker compose` 前设置；未设置时使用本地占位默认值 `xinghua-erp-dev-only` |
| `TOKEN_SECRET` | JWT 签名密钥 | 生产环境必填，默认值仅供本地开发，务必替换为足够长的随机串 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | 对象存储账号 | 未设置时使用本地默认 `minioadmin` / `minioadmin` |
| `MINIO_ENDPOINT` / `MINIO_PUBLIC_URL` / `MINIO_BUCKET` | 对象存储地址与桶名 | 默认指向本机 MinIO |
| `ERP_AGENT_SECRET` | 后端调用 AI 服务的共享密钥 | 需与 AI 服务 `agent_secret` 同时设置且长度 ≥ 32 字符，否则 AI 内部接口一律返回 403 |
| `ERP_BACKEND_URL` | AI 服务回调后端的地址 | 默认 `http://127.0.0.1:6149`；工具调用经此回查业务数据，需与后端实际监听地址一致 |

示例（Git Bash）：

```bash
export DB_PASSWORD=xinghua-erp-dev-only
export MYSQL_ROOT_PASSWORD=xinghua-erp-dev-only
export TOKEN_SECRET="$(openssl rand -hex 32)"
```

AI 服务的本地配置：复制 `ai-service/config.example.json` 为 `ai-service/config.local.json` 后填写 `agent_secret`、
`listing_rules` 等字段。模板里的 `REPLACE_ME` 只是占位符，必须替换为真实值，且
`agent_secret` 长度需 ≥ 32 字符（不足时 AI 内部接口会直接返回 403）。`config.local.json` 已在 `.gitignore` 中排除，
**不会被提交到仓库**；也不要把真实密钥写回 `config.example.json`（该文件仅作模板随仓库分发）。
其中的 `model` 段仅作为页面未配置时的回退，页面配置生效时会被请求头里的参数覆盖。
