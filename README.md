# 兴华电商 ERP

面向多商户电商企业的进销存管理系统。当前处于基础骨架阶段：先把技术基座与领域模块拆分定下来，再逐步落地各业务链路。

## 技术栈

- 后端：Java 21、Spring Boot 4.1、MyBatis-Plus、MySQL 8、Redis、JWT
- 前端：Vue 3、Vite（后续接入）
- 构建：Maven 多模块，模块化单体（Modular Monolith）

## 模块划分

| 模块 | 职责 |
| --- | --- |
| `erp-common` | 技术基座：统一结果、异常体系、分页、缓存、通用配置与工具 |
| `erp-system` | 系统与主数据：用户、角色、权限、商户、店铺，认证授权 |
| `erp-product` | 商品：SPU/SKU、类目、品牌 |
| `erp-inventory` | 库存：仓库、库位、出入库、锁定、调拨 |
| `erp-purchase` | 采购：采购单、采购入库、供应商 |
| `erp-order` | 订单：同步、处理、售后、退款 |
| `erp-fulfillment` | 履约：发货、物流、电子面单 |
| `erp-finance` | 财务与报表：成本、费用与账本 |
| `erp-channel` | 渠道接入：平台开放接口 SDK 与授权 |
| `erp-api` | 启动与组装：全局配置、REST、定时任务 |

## 快速开始

```bash
mvn -f erp-backend/pom.xml -pl erp-common -am clean install   # 构建基础模块
mvn -f erp-backend/pom.xml -pl erp-api -am spring-boot:run    # 装配层落地后启动
```

## 目录结构

```
erp-backend/     Spring Boot 多模块后端（按业务领域拆分）
docs/            设计文档与数据库脚本
```
