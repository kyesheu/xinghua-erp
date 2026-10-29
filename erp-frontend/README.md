# ERP 管理前端

前端提供商品、订单、采购、库存、财务、系统管理和 AI 页面。采用 Vue 3、TypeScript、Vite 8 与 Element Plus，业务 API 由 Java 后端提供。

## 启动与访问

准备 Node.js 22.12 或以上，并先按[部署指南](../docs/部署指南.md)启动后端。在本目录执行：

```powershell
npm install
npm run dev
```

默认访问 http://localhost:88，Java 后端默认地址为 http://localhost:6149。

## 地址配置

| 配置文件 | 使用场景 |
| --- | --- |
| `.env.development` | 本机页面开发服务 |
| `.env.staging` | staging 模式 |
| `.env.production` | 生产构建 |

`VITE_APP_BASE_API` 指定 API 地址，`VITE_APP_SSE_URL` 指定流式消息地址。静态部署时须与 nginx 反向代理路径一致；修改后重新启动开发服务或重新构建。

```powershell
npm run build
npm run preview
```

构建产物位于 `dist/`。`preview` 用于本机预览，正式部署由静态服务器提供页面，并为 API 与 SSE 配置代理。

## 页面与权限

菜单由后端返回，页面可见性取决于账号角色权限。业务数据还受商户、店铺、仓库等主体归属限制；隐藏菜单不代替服务端权限校验。首次操作顺序见[业务流程](../docs/采购销售库存业务流程.md)。

页面帮助位于 `public/help/`，内容面向操作人员；`src/views/` 为业务页面，`src/api/` 为接口封装，`src/utils/request.ts` 统一处理认证、请求和错误提示。
