import request from '@/utils/request'
const base = '/api/erp-api/afterSale/local'
export const findAfterSaleItems = (params: Record<string, any>) => request({ url: base + '/orderItems', method: 'get', params })
export const createLocalAfterSale = (data: Record<string, any>) => request({ url: base + '/create', method: 'post', data })
export const getLocalAfterSale = (refundNum: string) => request({ url: base + '/detail', method: 'get', params: { refundNum } })
export const processLocalAfterSale = (refundNum: string) => request({ url: base + '/process', method: 'post', data: { refundNum } })
export const confirmLocalAfterSale = (data: Record<string, any>) => request({ url: base + '/confirm', method: 'post', data })
export const completeLocalAfterSale = (data: Record<string, any>) => request({ url: base + '/complete', method: 'post', data })
export const cancelLocalAfterSale = (refundNum: string) => request({ url: base + '/cancel', method: 'post', data: { refundNum } })

export const confirmSupplierReissue = (data: Record<string, any>) => request({ url: base + '/confirmSupplierReissue', method: 'post', data })
