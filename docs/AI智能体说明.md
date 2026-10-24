# AI 智能体说明

AI 智能体为商家提供自然语言经营分析与人机协同的补货审核，采用 **Java 权限门禁 + Python LangGraph 工作流**双栈实现。

## 职责边界

**Java 不调用模型，Python 不直连数据库。**

| 环节 | 由谁负责 |
| --- | --- |
| 身份、权限、主体数据范围校验 | Java |
| 业务数据组装与只读查询 | Java（`ai-data` 只读接口 + 工具网关） |
| 模型构建、提示词、工具选择、多轮循环、流式输出 | Python（`ai-service`） |
| 服务不可用时的兜底 | Java（本地数据摘要 / 固定文案简报 / 可读提示） |

对话与分析都走同一条链路：前端 SSE → Java 组装上下文 → Python 调用模型并自行决定查什么 → Python 经工具网关回调 Java 取数 → 结果逐段回流。

## 核心设计原则

**模型不碰库、不拿凭据、不决定口径。** 所有数据访问由 Java 侧实时校验权限与主体范围后，以只读聚合接口返回结构化事实；Python 工作流用 Pydantic（extra=forbid）校验数据契约；口径限制说明（LIMITATION）由 Java 统一生成下发，LLM 仅对事实做文案汇总。

**工具令牌绑定发起人。** Python 回调取数时携带 Java 签发的短时令牌（HMAC，含用户标识与过期时间，5 分钟有效）。
工具网关还要求「仅本机来源 + 共享密钥匹配」，因此即使 AI 服务被他人直接访问，也无法伪造身份取走别人的数据。

## 功能清单

### AI 助手对话

- 自然语言问答，模型自行决定调用哪些工具、需要什么筛选条件，多轮取数后汇总作答
- 可用数据：订单、退款、库存、出入库、采购、物流、商品、SKU、会员、供应商、仓库、店铺（全部只读）
- Java 只负责组装上下文、保存会话历史与转发流式事件；AI 服务不可用时返回可读提示，不留下空白流

### 经营分析助手（多主题并行）

- 用户自然语言提问，LLM 任务规划器按问题路由到销售、财务、售后、库存主题（不支持的问题显式返回 unsupported，不猜测）
- `Send` API 将可用主题节点**同一超步并行执行**（fan-out），`operator.add` Reducer 聚合各节点状态，按固定业务顺序排序后生成经营报告
- 数据覆盖：本期/上期销售对比、支付与退款差额、已结算订单利润、SKU 表现排行（含退款归集、四象限、TOP5/10/20 集中度）、负利润订单及诱因分布、多店对比、库存风险分布
- 可选接入 LLM 生成结论文案（`useModel` 显式开启），关闭时输出纯规则汇总

### 明细导出

- SKU 表现明细、负利润订单明细导出 CSV（UTF-8 BOM、列白名单、5000 行上限截断提示）
- 导出与查询走完全相同的权限校验，不含任何买家个人信息

### 库存补货审核（人机闭环）

- AI 汇总库存与销量资料生成补货建议，`interrupt` 挂起等待人工审核，checkpoint 持久化（服务重启可恢复）
- 人工确认后流程恢复，生成采购草稿进入既有采购流程；草稿创建幂等且与记录同一事务提交
- 多进程经数据库会话锁串行化处理同一审核任务
- 产品定位：**AI 汇总资料、人决定补货**——审核人与采购流程是权威来源

### 商品文案优化

- 基于 Java 商品资料生成 Listing 草稿（标题、卖点、描述、关键词），按站点规则检查（标题长度、禁用词），草稿需人工确认后才可使用

## 目录与代码位置

| 组件 | 位置 |
| --- | --- |
| Python 工作流 | `ai-service/app/workflows/`（management 并行分析、inventory_review 人机审核、product 文案、agent 对话与分析编排）；配置在 `app/core/config.py`、共享模型在 `app/schemas/`、规则在 `app/rules/` |
| Python 工具定义 | `ai-service/app/tools/erp.py`（`query_erp`：13 类只读数据查询，经 HTTP 回调 Java） |
| 工作流测试 | `ai-service/tests/`（Mock LLM 离线回归，不依赖付费模型） |
| Java 只读资料服务 | `erp-backend/service/`（AiSalesReadService、AiFinanceReadService、AiInventoryReadService 等） |
| Java 权限门禁与端点 | `erp-backend/erp-api/.../controller/ai/`（ManagementAgentController 等） |
| Java 工具网关 | `AiToolGateway`（`/internal/ai-tools/query`，白名单分发 13 类查询）+ `ToolToken`（短时令牌） |
| Java 兜底 | `ChatService`（可读提示）、`AiAnalysisFallback`（本地数据摘要）、`AiBriefService#buildFallbackBrief`（固定文案简报） |
| 前端页面 | `erp-frontend/src/views/ai/` |
| 数据库脚本 | `docs/sql/ai-*.sql`（AI 表与权限项，见[部署指南](部署指南.md)） |

## 模型配置

- LLM 在「AI 智能 > 模型配置」页面配置（OpenAI 兼容接口），后端调用本服务时经请求头透传模型地址、密钥与模型名；
  对话、深度分析、工作台简报与「测试连接」共用这一处配置，Java 侧不再构造任何模型客户端；
  页面未配置时回退到环境变量 `AI_MODEL_ENABLED`、`AI_MODEL_NAME`、`AI_MODEL_API_KEY`、`AI_MODEL_BASE_URL` 与 `config.local.json` 的 `model` 段
- `AI_MODEL_ENABLED=false` 时任务规划与文案生成走纯规则路径，全部功能仍可验证，不依赖付费模型
- 服务间调用使用 `ERP_AGENT_SECRET` 鉴权
- 详细启动步骤见 [部署指南](部署指南.md)
