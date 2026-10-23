import request from '@/utils/request'
export interface SettlementSummary {
  available:boolean;settledOrders:number;salesAmount:number|null;purchaseCost:number|null;shippingFee:number|null
  platformFee:number|null;marketingFee:number|null;otherFee:number|null;totalCost:number|null;profit:number|null
  zeroCostSettled:number;unsettledPaidOrders:number
}
export interface FinanceOverview {
  scope:string; days:number; start:string; endExclusive:string; retrievedAt:string
  orders:{count:number;payment:number;negativePaymentRows:number}
  refunds:{count:number;amount:number;invalidRows:number;unmatchedRows:number;ambiguousRows:number}
  paymentLessRefund:number;profit:number|null
  settlement:SettlementSummary
  ledger:{available:boolean;entries:number;income:number|null;expense:number|null;invalidRows:number;accounts:{accountType:string|null;entries:number;income:number;expense:number}[];limitation:string}
  missingInputs:string[];limitation:string
}
export function getFinanceOverview(days:number) {
  return request<any,{data:FinanceOverview}>({url:'/api/erp-api/ai-data/finance/overview',params:{days}})
}

export interface LossOrder {
  orderNo:string;salesAmount:number;purchaseCost:number;shippingFee:number;platformFee:number
  marketingFee:number;otherFee:number;totalCost:number;profit:number;causes:string[]
}
export interface LossOrders {
  available:boolean;days:number;start:string;endExclusive:string;retrievedAt:string
  count:number;truncated:boolean;orders:LossOrder[];causeCounts:Record<string,number>;limitation:string
}
export interface ShopComparison {
  shopId:string|null;orders:number;payment:number;refundAmount:number
  settledOrders:number;settledSalesAmount:number|null;profit:number|null
}
export interface ShopComparisonResult {
  days:number;start:string;endExclusive:string;retrievedAt:string;shops:ShopComparison[];limitation:string
}
export function getLossOrders(days:number) {
  return request<any,{data:LossOrders}>({url:'/api/erp-api/ai-data/finance/loss-orders',params:{days}})
}
/** 负利润订单 CSV 导出（UTF-8 with BOM），返回文件流。 */
export function exportLossOrders(days:number) {
  return request<any,Blob>({
    url:'/api/erp-api/ai-data/finance/loss-orders/export',params:{days},responseType:'blob',
  })
}
export function getShopComparison(days:number) {
  return request<any,{data:ShopComparisonResult}>({url:'/api/erp-api/ai-data/finance/shop-comparison',params:{days}})
}
