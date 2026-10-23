import request from '@/utils/request'

interface Metrics { orders: number; paidOrders: number; payment: number }
export interface SalesOverview {
  scope: string; days: number; start: string; endExclusive: string; retrievedAt: string
  current: Metrics; previous: Metrics
  trend: { day: string; orders: number; payment: number }[]
  statuses: { status: number | null; orders: number }[]
  shops: { shopId: string | null; orders: number; payment: number }[]
  products: { skuId: string; title: string | null; quantity: number }[]
  quality: { unlinkedRows: number; invalidQuantityRows: number }; limitation: string
}
export function getSalesOverview(days: number) {
  return request<any, { data: SalesOverview }>({ url: '/api/erp-api/ai-data/sales/overview', params: { days } })
}

export interface SkuRow {
  skuId: string; title: string | null; paidOrders: number; quantity: number; salesAmount: number
  refundOrders: number; refundAmount: number; refundRate: number | null; quadrant: string | null
}
export interface SkuPerformance {
  days: number; start: string; endExclusive: string; retrievedAt: string
  skuCount: number; unlinkedRefundRows: number; medianQuantity: number | null; medianRefundRate: number | null
  topShares: { top5: number | null; top10: number | null; top20: number | null }
  quadrantCounts: Record<string, number>; quadrantSales: Record<string, number>
  rows: SkuRow[]; limitation: string
}
export function getSkuPerformance(days: number) {
  return request<any, { data: SkuPerformance }>({ url: '/api/erp-api/ai-data/sales/sku-performance', params: { days } })
}
/** SKU 表现排行 CSV 导出（UTF-8 with BOM），返回文件流。 */
export function exportSkuPerformance(days: number) {
  return request<any, Blob>({
    url: '/api/erp-api/ai-data/sales/sku-performance/export', params: { days }, responseType: 'blob',
  })
}
