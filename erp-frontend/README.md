# erp-frontend

电商 ERP 的前端工程：Vue 3 + TypeScript + Vite + Element Plus，与 `erp-backend`（端口 6149）、`ai-service`（端口 8000）配合使用。

## 运行

```bash
npm install
npm run dev       # 开发服务，http://localhost:88
npm run build     # 类型检查 + 生产构建，输出 dist/
npm run preview   # 预览构建产物
```

需要 Node.js 18+。

## 环境变量

按 `mode` 读取对应文件，均以 `VITE_` 开头：

| 文件 | 用途 | 关键变量 |
| --- | --- | --- |
| `.env.development` | 本地开发 | `VITE_APP_BASE_API=http://localhost:6149` |
| `.env.staging` | 预发 | `VITE_APP_BASE_API=/stage-api`（配合 nginx 反代） |
| `.env.production` | 生产构建 | `VITE_APP_BASE_API=/prod-api`（配合 nginx 反代） |

`VITE_APP_SSE_URL` 是 SSE 推送地址（消息通知、AI 对话流式输出），开发环境与后端同址；部署到 nginx 时改为反代路径。开发服务器监听 88 端口，`/api` 请求按 `VITE_APP_BASE_API` 代理到后端。

## 目录结构

```
src/
├── api/            # 接口封装，按业务域分目录（goods/order/inventory/ai/system…）
├── views/          # 页面，按业务域分目录
├── store/modules/  # Pinia 状态；permission.ts 负责动态路由（后端菜单 → 视图组件）
├── router/         # 路由定义与守卫
├── utils/          # request.ts（axios 封装、令牌与错误处理）、通用工具
├── components/     # 通用组件
└── assets/         # 样式、图标、图片
public/help/        # 页面内帮助文档（Markdown）
```

## 约定

- 菜单与路由由后端返回，`store/modules/permission.ts` 把菜单的组件路径映射到 `views/` 下的文件；新增页面需同步在后端菜单中登记。
- 接口统一走 `utils/request.ts`，令牌、错误提示与登录失效跳转都在这一层处理，页面里不要直接用 axios。
- 类型检查命令为 `npx vue-tsc --noEmit -p tsconfig.app.json`，必须带 `-p`（根 `tsconfig.json` 是 solution 型，直接跑不会真正检查代码）。
