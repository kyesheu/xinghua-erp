import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'

export const constantRoutes = [
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect/index.vue'),
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    hidden: true,
    meta: { title: '登录' },
  },
  {
    path: '/401',
    name: 'Page401',
    component: () => import('@/views/error/401.vue'),
    hidden: true,
    meta: { title: '401' },
  },
  {
    path: '/404',
    name: 'Page404',
    component: () => import('@/views/error/404.vue'),
    hidden: true,
    meta: { title: '404' },
  },
  {
    path: '/help',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '',
        name: 'Help',
        component: () => import('@/views/help/index.vue'),
        meta: { title: '帮助中心' },
      },
    ],
  },
  {
    path: '/',
    name: 'Index',
    component: Layout,
    redirect: '/index',
    children: [
      {
        path: 'index',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'dashboard' },
      },
      {
        path: 'inventory-agent',
        name: 'InventoryAgent',
        component: () => import('@/views/ai/inventoryAgent.vue'),
        hidden: true,
        meta: { title: '库存采购助手' },
      },
      {
        path: 'management-agent',
        name: 'ManagementAgent',
        component: () => import('@/views/ai/managementAgent.vue'),
        hidden: true,
        meta: { title: '经营资料汇总' },
      },
      {
        path: 'risk-agent',
        name: 'RiskAgent',
        component: () => import('@/views/ai/riskAgent.vue'),
        hidden: true,
        meta: { title: '风险与售后资料助手' },
      },
      {
        path: 'marketing-agent',
        name: 'MarketingAgent',
        component: () => import('@/views/ai/marketingAgent.vue'),
        hidden: true,
        meta: { title: '广告与营销资料助手' },
      },
      {
        path: 'product-agent',
        name: 'ProductAgent',
        component: () => import('@/views/ai/productAgent.vue'),
        hidden: true,
        meta: { title: '商品运营助手' },
      },
      {
        path: 'sales-agent',
        name: 'SalesAgent',
        component: () => import('@/views/ai/salesAgent.vue'),
        hidden: true,
        meta: { title: '销售与订单助手' },
      },
      {
        path: 'finance-agent',
        name: 'FinanceAgent',
        component: () => import('@/views/ai/financeAgent.vue'),
        hidden: true,
        meta: { title: '利润与财务资料助手' },
      },
      {
        path: 'user/profile',
        name: 'Profile',
        component: () => import('@/views/system/user/profile/index.vue'),
        hidden: true,
        meta: { title: '个人中心' },
      },
    ],
  },
  {
    // 先匹配刷新时尚未注册的动态路由，由权限守卫加载菜单后重新匹配。
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    hidden: true,
    meta: { title: '404' },
  },
] as RouteRecordRaw[]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
})

export default router

