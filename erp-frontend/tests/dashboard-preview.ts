// Isolated visual QA entry. The production app does not import this file.
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Dashboard from '../src/views/dashboard/index.vue'
import request from '../src/utils/request'

request.defaults.baseURL = ''
const paths = ['/', '/sale/order_list', '/purchase/purchase_order', '/wms/goods_stock', '/wms/stocking', '/sale/shop_refund', '/wms/stock_in_list', '/wms/stock_out_list']
const router = createRouter({ history: createMemoryHistory(), routes: paths.map((path, i) => ({ path, name: `preview-${i}`, component: Dashboard })) })
createApp(Dashboard).use(createPinia()).use(router).use(ElementPlus).mount('#app')
