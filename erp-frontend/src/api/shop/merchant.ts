import request from '@/utils/request'

export function listMerchant(query?: Record<string, any>) {
  return request({
    url: '/api/oms-api/merchant/list',
    method: 'get',
    params: query
  })
}

/** 管理列表包含启用、停用商户；不包含供下拉选择使用的总部虚拟记录。 */
export function listMerchantPage(query?: Record<string, any>) {
  return request({ url: '/api/oms-api/merchant/pageList', method: 'get', params: query })
}

export function pushGoodsToMerchant(data: Record<string, any>) {
  return request({
    url: '/api/erp-api/goods/pushGoodsToMerchant',
    method: 'post',
    data
  })
}

export function getMerchant(id: number | string) {
  return request({ url: '/api/oms-api/merchant/' + id, method: 'get' })
}

export function addMerchant(data: Record<string, any>) {
  return request({ url: '/api/oms-api/merchant/add', method: 'post', data })
}

export function updateMerchant(data: Record<string, any>) {
  return request({ url: '/api/oms-api/merchant/edit', method: 'put', data })
}

export function delMerchant(id: number | string) {
  return request({ url: '/api/oms-api/merchant/del/' + id, method: 'delete' })
}
