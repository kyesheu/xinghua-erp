import request from '@/utils/request'

export interface InventoryOverviewRow {
  sales30Days?: number | null
  openPurchaseOrders?: number | null
  skuId: string; skuCode: string | null; goodsName: string | null; skuName: string | null
  productStatus: number | null; lowQty: number | null; inventoryRows: number; goodRows: number
  availableQuantity: number | null; risk: string
  quantity: number | null; lockedQuantity: number | null
  emptyScopes: { warehouseId: string | null; merchantId: string | null; shopId: string | null }[]
}
export interface InventoryOverview {
  total: number; page: number; size: number; scope: string; limitation: string; emptyScopesTruncated: boolean; records: InventoryOverviewRow[]
  /** 请求账号主体类型（HEADQUARTERS/MERCHANT/SHOP/...），前端用于隐藏总部专属采购卡片 */
  subjectType: string
}
export function getInventoryOverview(page = 1, size = 50) {
  return request<any, { data: InventoryOverview }>({ url: '/api/erp-api/ai-data/inventory/overview', params: { page, size } })
}

export interface PurchaseShipment {
  id: string; orderId: string; orderNum: string; warehouseId: string | null
  shipCompany: string | null; shipNum: string | null; shipTime: string | null; receiptTime: string | null; status: number | null
}
export interface InventoryDossier {
  retrievedAt: string
  inventory: { records: { id: string | number; warehouseId: number | null; merchantId: number | null; shopId: number | null; quantity: number | null; lockedQuantity: number | null; availableQuantity: number | null; stockStatus: number | null; updateTime: string | null }[]; scope: string; limitation: string }
  batchAudit: { inventoryId: string | number; batchCount: string | number; batchQuantity: string | number; invalidBatches: string | number }[]
  sales7Days: { quantity: number; start: string; endExclusive: string; limitation: string } | null
  sales30Days: { quantity: number; start: string; endExclusive: string; limitation: string } | null
  purchase: { shipments: PurchaseShipment[]; ledger: Record<string, unknown>[]; records: Record<string, unknown>[]; withoutTask: Record<string, unknown>[]; limitation: string } | null
  salesVisible: boolean; purchaseVisible: boolean
}
export function getInventoryDossier(skuId: string) {
  return request<any, { data: InventoryDossier }>({ url: '/api/erp-api/ai-data/inventory/dossier', params: { skuId } })
}

export interface InventoryAgentResult {
  purchaseChecks: { sourceNo: string | null; warehouseId: number | null; status: string; remainingQuantity: number | null; issues: string[] }[]
  inventoryChecks: { warehouseId: number | null; merchantId: number | null; shopId: number | null; status: string; issues: string[]; availableQuantity: number | null }[]
  coverage: { arrivalProjection: { firstShortageDay: string | null; endBalance: string; steps: { day: number; incoming: number; beforeArrival: string; afterArrival: string }[]; limitation: string } | null; effectiveIncoming: number; incomingArrivalDays: number | null; leadTimeDays: number | null; timingRisks: string[]; dailyDemand: string; targetDays: number; availableQuantity: number; confirmedIncoming: number; currentCoverageDays: string; targetDemand: string; rule: string; limitation: string } | null
  summary: string
  modelStatus: string
  modelExplanation?: string
  suggestedQuantity: number | null
  warnings: string[]
  facts: {
    skuId: number; warehouseId: number | null; days: number; retrievedAt: string
    inventory: { warehouseId: number; merchantId: number; shopId: number; quantity: number | null; availableQuantity: number | null; lockedQuantity: number | null; stockStatus: number | null }[]
    sales: { quantity: number; start: string; endExclusive: string; scope: string; limitation: string } | null
    purchase: { shipments?: PurchaseShipment[]; records: Record<string, unknown>[]; withoutTask: Record<string, unknown>[]; ledger: Record<string, unknown>[]; limitation: string } | null
    assumptions: { dailyDemand: string; targetDays: number; confirmedIncoming: number; incomingArrivalDays?: number | null; leadTimeDays?: number | null; incomingBatches?: { quantity: number; arrivalDays: number }[] | null } | null
  }
}
export function getInventoryAnalysis(skuId: string, warehouseId: string | undefined, days: number,
  assumptions?: { dailyDemand: string; targetDays: string; confirmedIncoming: string; incomingArrivalDays?: string; leadTimeDays?: string; incomingBatches?: { quantity: string; arrivalDays: string }[] }, question = '') {
  return request<any, { data: InventoryAgentResult }>({ url: '/api/erp-api/ai-agent/inventory/analysis', method: 'post', data: { skuId, warehouseId, days, ...assumptions, question }, timeout: 35000 })
}

export interface InventorySkuChoice { skuId: string; skuCode: string | null; goodsName: string | null; skuName: string | null }
export function searchInventorySku(keyword: string) {
  return request<any, { data: InventorySkuChoice[] }>({ url: '/api/erp-api/ai-data/inventory/search', params: { keyword } })
}

export function createPurchaseDraft(data: { requestKey: string; skuId: string; supplierId: string; quantity: string; unitPrice: string; confirmed: boolean }) {
  return request<any, { data: { orderId: string; replayed: boolean } }>({ url: '/api/erp-api/ai-agent/inventory/purchase-draft', method: 'post', data })
}
export function getPurchaseSuppliers(skuId: string) {
  return request<any, { data: { id: string; name: string }[] }>({ url: '/api/erp-api/ai-agent/inventory/purchase-suppliers', params: { skuId } })
}

export function getPurchaseProgress(orderId: string) {
  return request<any, { data: { id: string; orderNum: string; status: number; auditUser: string | null; updateTime: string | null } | null }>({ url: `/api/erp-api/erp/purchase/detail/${orderId}` })
}

export interface RecentPurchaseDraft { orderId: string; skuId: string; quantity: number; unitPrice: number; orderNum: string | null; status: number | null }
export function getRecentPurchaseDrafts() {
  return request<any, { data: RecentPurchaseDraft[] }>({ url: '/api/erp-api/ai-agent/inventory/purchase-drafts' })
}

export interface InventoryReviewTask {
  id: string; createdBy: string; skuId: string; warehouseId: string; suggestedQuantity: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'; version: number; createdAt: string
  reviewedBy: string | null; reviewedAt: string | null; reviewNote: string | null
  approvedQuantity: number | null
  resultJson: string
}
export function createInventoryReview(data: Record<string, unknown>, requestKey: string) {
  return request<any, { data: { id: string } }>({ url: '/api/erp-api/ai-agent/inventory/review-tasks', method: 'post', data, headers: { 'X-Request-Key': requestKey }, timeout: 35000 })
}
export function listInventoryReviews() {
  return request<any, { data: InventoryReviewSummary[] }>({ url: '/api/erp-api/ai-agent/inventory/review-tasks' })
}
export type InventoryReviewSummary = Pick<InventoryReviewTask, 'id' | 'createdBy' | 'skuId' | 'warehouseId' | 'suggestedQuantity' | 'status' | 'version' | 'createdAt'>
export function getInventoryReview(id: string) {
  return request<any, { data: InventoryReviewTask }>({ url: `/api/erp-api/ai-agent/inventory/review-tasks/${id}` })
}
export function decideInventoryReview(id: string, version: number, approved: boolean, note: string, approvedQuantity: number | null) {
  return request<any, { data: InventoryReviewTask }>({ url: `/api/erp-api/ai-agent/inventory/review-tasks/${id}/decision`, method: 'post', data: { version, approved, note, approvedQuantity }, timeout: 35000 })
}

export function createReviewedPurchaseDraft(id: string, data: { version: number; supplierId: string; unitPrice: string; confirmed: boolean }) {
  return request<any, { data: { orderId: string; replayed: boolean } }>({ url: `/api/erp-api/ai-agent/inventory/review-tasks/${id}/purchase-draft`, method: 'post', data, timeout: 35000 })
}

/** 手动同步审核工作流检查点：返回 waiting=检查点是否仍在等待审核决定，decision=已恢复的决定 */
export interface InventoryReviewWorkflowSync { taskId: string; waiting: boolean; decision: { approved: boolean } | null }
export function syncInventoryReviewWorkflow(id: string) {
  return request<any, { data: InventoryReviewWorkflowSync }>({ url: `/api/erp-api/ai-agent/inventory/review-tasks/${id}/workflow-sync`, method: 'post', timeout: 35000 })
}
