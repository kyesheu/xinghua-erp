import request from '@/utils/request'

export interface DashboardOverview {
  startDate: string
  endDate: string
  updatedAt: string
  scopeLabel: string
  merchantId: number | null
  shopId: number | null
  summary: {
    grossAmount: number
    refundAmount: number
    netAmount: number
    orderCount: number
    afterSaleCount: number
    averageOrderAmount: number
    stockQuantity: number
    availableQuantity: number
    lowStockCount: number
  }
  daily: { date: string; grossAmount: number; refundAmount: number; netAmount: number; orderCount: number }[]
  shops: Rank[]
  products: Rank[]
  orderStatuses: Slice[]
  afterSaleTypes: Slice[]
  warehouses: { id: string; name: string; quantity: number; available: number; locked: number; lowStockCount: number }[]
  tasks: { waitShip: number; waitInbound: number; waitOutbound: number; waitAfterSale: number }
}
export interface Rank { id: string; name: string; image: string; amount: number; quantity: number }
export interface Slice { code: number; name: string; value: number }
export function getDashboardOverview(days: 7 | 30) {
  return request<any, { data: DashboardOverview }>({ url: '/api/erp-api/dashboard/overview', params: { days } })
}

// 工作台 AI 简报（与 AI 助手模块无关，属工作台数据）
export interface QuickStats {
  salesVolume: string
  salesTrend?: string
  orderCount: number
  waitShip: number
  refundRate?: string
}

export interface Priority {
  level: 'high' | 'medium' | 'low'
  category: string
  title: string
  detail: string
  action: string
  link: string
  count: number
}

export interface AiBriefResponse {
  greeting: string
  summary: string
  trend?: string
  aiAvailable: boolean
  quickStats: QuickStats
  priorities: Priority[]
}

export function getAiBrief() {
  return request({
    url: '/api/ai/brief',
    method: 'get',
    timeout: 45000,
  })
}
