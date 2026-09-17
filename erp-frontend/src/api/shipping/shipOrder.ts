import request from '@/utils/request'

export function manualShipmentOrder(data: Record<string, any>) {
  return request({ url: '/api/erp-api/ship/order/manualShipment', method: 'post', data })
}
export function pushOrderToSupplier(data: Record<string, any>) {
  return request({ url: '/api/erp-api/ship/vendor/pushOrderToSupplier', method: 'post', data })
}
export function pushOrderItemToCloudWarehouse(data: Record<string, any>) {
  return request({ url: '/api/erp-api/ship/order/push_order_item_to_cloud_warehouse', method: 'post', data })
}
export function supplierItemRemaining(ids: Array<number | string>) {
  return request({ url: '/api/erp-api/ship/order/supplier_item_remaining', method: 'post', data: { ids } })
}
export function pushOrderItemToSupplier(data: Record<string, any>) {
  return request({ url: '/api/erp-api/ship/order/push_order_item_to_supplier', method: 'post', data })
}
export function pushToCloudWarehouse(data: Record<string, any>) {
  return request({ url: '/api/erp-api/ship/cloudWarehouse/pushToCloudWarehouse', method: 'post', data })
}
export function pushAgainToCloudWarehouse(data: Record<string, any>) {
  return request({ url: '/api/erp-api/ship/cloudWarehouse/pushAgainToCloudWarehouse', method: 'post', data })
}

export function allocateLocalItems(data: Record<string, any>) {
  return request({ url: '/api/erp-api/ship/order/allocate_local_items', method: 'post', data })
}
export function confirmLocalTask(data: Record<string, any>) {
  return request({ url: '/api/erp-api/ship/order/local_task_confirm', method: 'post', data })
}
export function cancelLocalTask(id: number | string) {
  return request({ url: '/api/erp-api/ship/order/local_task_cancel', method: 'post', data: { id } })
}
