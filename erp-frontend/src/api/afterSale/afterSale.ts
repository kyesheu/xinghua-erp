import request from '@/utils/request'

/** 查询售后处理台账；本地操作接口见 local.ts。 */
export function list(query?: Record<string, any>) {
  return request({ url: '/api/erp-api/afterSale/list', method: 'get', params: query })
}
