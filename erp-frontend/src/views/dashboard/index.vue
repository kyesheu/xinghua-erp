<template>
  <div ref="screen" class="data-screen" :aria-busy="overviewLoading">
    <header class="screen-header">
      <div class="brand"><div class="brand-mark"><el-icon><DataAnalysis /></el-icon></div><div><h1>经营概览<span>首页</span></h1></div></div>
      <div class="controls"><time>{{ clockText }}</time><div class="period-switch" aria-label="统计周期"><button v-for="period in periods" :key="period" :class="{ active: days === period }" :aria-pressed="days === period" @click="days = period">近 {{ period }} 日</button></div><button class="icon-button" :disabled="overviewLoading" aria-label="刷新经营数据" @click="load(true)"><el-icon :class="{ rotating: overviewLoading }"><Refresh /></el-icon></button><button class="icon-button" :aria-label="fullscreen ? '退出全屏' : '全屏查看'" @click="toggleFullscreen"><el-icon><FullScreen /></el-icon></button></div>
    </header>
    <div class="scope-line"><span class="scope-badge">{{ overview?.scopeLabel || '经营数据' }}</span><span>{{ overview ? `${overview.startDate} — ${overview.endDate}` : `近 ${days} 日` }}</span><span class="updated">{{ overview ? `更新于 ${overview.updatedAt.replace('T', ' ').slice(0, 19)}` : '正在连接经营数据' }}</span></div>
    <div v-if="error" class="error-banner" role="alert">{{ overview ? '更新失败，保留上次数据。' : '经营数据加载失败。' }}{{ error }}<button @click="load(true)">重新加载</button></div>
    <section class="metrics-grid" aria-label="核心经营指标"><div v-for="metric in metrics" :key="metric.label" class="metric" :style="{ '--accent': metric.color }"><div class="metric-top"><span>{{ metric.label }}</span><el-icon><component :is="metric.icon" /></el-icon></div><div class="metric-value"><small v-if="metric.money">¥</small>{{ metric.value }}<small>{{ metric.unit }}</small></div><div class="metric-caption">{{ metric.caption }}</div></div></section>
    <section class="visual-grid top-grid" aria-label="销售与订单分析">
      <article class="panel shop-panel"><div class="panel-heading"><h2>店铺销售排行</h2><span>TOP 6 · 元</span></div><div class="chart-area"><BoardChart :option="shopOption" label="按下单金额排序的前六家店铺" /></div><div class="panel-foot">按期间原订单实付金额排名</div></article>
      <article class="panel trend-panel"><div class="panel-heading"><h2>销售走势</h2><span>{{ days }} 日经营趋势</span></div><div class="chart-area"><BoardChart :option="trendOption" label="每日下单金额、扣退款后净额与订单量趋势" /></div><div class="panel-foot">按原单日期归属<span>缺少订单的日期按 0 展示</span></div></article>
      <article class="panel order-panel"><div class="panel-heading"><h2>订单状态</h2><span>已付款订单</span></div><div class="chart-area"><BoardChart :option="orderOption" label="所选期间已付款订单状态分布" /></div><div class="panel-foot">{{ summary ? `${integer(summary.orderCount)} 笔订单 · 含退款关闭订单` : '等待经营数据' }}</div></article>
    </section>
    <section class="visual-grid bottom-grid" aria-label="售后、商品与库存分析">
      <article class="panel after-panel"><div class="panel-heading"><h2>售后类型分布</h2><span>申请单数</span></div><div class="chart-area"><BoardChart :option="afterOption" label="售前退款、仅退款、退货退款、换货、补发申请数量" /></div><div class="panel-foot">已登记退款 <strong>{{ summary ? money(summary.refundAmount) : '—' }}</strong> 元</div></article>
      <article class="panel products-panel"><div class="panel-heading"><h2>热销商品</h2><span>TOP 5 · 下单金额</span></div><div class="products-list"><div v-for="(product, index) in overview?.products || []" :key="product.id" class="product-row"><span class="rank-number" :class="{ podium: index < 3 }">{{ String(index + 1).padStart(2, '0') }}</span><el-image :src="product.image" fit="cover" class="product-image"><template #error><el-icon><Box /></el-icon></template></el-image><div class="product-info"><div class="product-name" :title="product.name">{{ product.name }}</div><div class="product-track"><span :style="{ width: `${productMax ? Number(product.amount) / productMax * 100 : 0}%` }" /></div></div><div class="product-sales"><strong>¥{{ money(product.amount) }}</strong><span>{{ integer(product.quantity) }} 件</span></div></div><div v-if="!overview?.products.length" class="empty-state">{{ placeholder }}</div></div><div class="panel-foot">原订单件数，退回及补发另记仓库流水</div></article>
      <article class="panel inventory-panel"><div class="panel-heading"><h2>仓库库存分布</h2><span class="live-tag">● 当前库存</span></div><div class="chart-area"><BoardChart :option="warehouseOption" label="各仓库当前可用和锁定库存数量" /></div><div class="panel-foot">{{ overview ? `${overview.warehouses.length} 个仓库 · ${integer(summary?.lowStockCount || 0)} 组 SKU 达到预警线` : '等待库存数据' }}</div></article>
    </section>
    <section class="tasks-strip" aria-label="当前待处理业务"><component :is="entry.available ? RouterLink : 'div'" v-for="entry in tasks" :key="entry.label" v-bind="entry.available ? { to: entry.to } : {}" class="task-item"><el-icon :style="{ color: entry.color }"><component :is="entry.icon" /></el-icon><div class="task-copy"><span>{{ entry.label }}</span><small>当前全部待办</small></div><strong :style="{ color: entry.count ? entry.color : '' }">{{ entry.value }}</strong><el-icon v-if="entry.available" class="task-arrow"><ArrowRight /></el-icon></component></section>
    <footer><div class="quick-links"><span>业务入口</span><RouterLink v-for="entry in quickEntries" :key="entry.path" :to="entry.path">{{ entry.label }} ↗</RouterLink></div><el-tooltip content="期间净额＝期间原订单实付－这些订单已登记完成的退款。库存与待办反映当前状态。" placement="top"><span class="data-note"><el-icon><InfoFilled /></el-icon>统计口径</span></el-tooltip></footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Refresh, FullScreen, Warning, Box, ArrowRight, InfoFilled, ShoppingCart, Tickets, RefreshLeft, Wallet, Van, Download, Upload } from '@element-plus/icons-vue'
import type { EChartsCoreOption } from 'echarts/core'
import type { Slice } from '@/api/dashboard'
import { useDashboardStore } from '@/store/modules/dashboard'
import { useUserStore } from '@/store/modules/user'
import BoardChart from './components/BoardChart.vue'

defineOptions({ name: 'Dashboard' })
const store = useDashboardStore()
const userStore = useUserStore()
const { overview, overviewLoading } = storeToRefs(store)
const router = useRouter()
const screen = ref<HTMLDivElement>()
const fullscreen = ref(false)
const days = ref<7 | 30>(7)
const periods = [7, 30] as const
const error = ref('')
const now = ref(new Date())
let timer: ReturnType<typeof setInterval> | undefined
const palette = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#9b7ef2', '#36b5c5']
const summary = computed(() => overview.value?.summary)
const integer = (v: number) => Number(v).toLocaleString('zh-CN')
const money = (v: number) => Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const clockText = computed(() => now.value.toLocaleString('zh-CN', { hour12: false }).replaceAll('/', '.'))
const placeholder = computed(() => overviewLoading.value ? '正在加载数据…' : overview.value ? '所选期间暂无数据' : '数据尚未加载')
const productMax = computed(() => Math.max(0, ...(overview.value?.products.map(p => Number(p.amount)) || [])))
const metrics = computed(() => {
  const s = summary.value
  return [
    { label: '期间下单金额', value: s ? money(s.grossAmount) : '—', money: true, unit: '', caption: '原订单实付 · 含后续退款订单', color: palette[0], icon: ShoppingCart },
    { label: '扣退款后净额', value: s ? money(s.netAmount) : '—', money: true, unit: '', caption: `已登记退款 ¥${s ? money(s.refundAmount) : '—'}`, color: palette[1], icon: Wallet },
    { label: '期间订单量', value: s ? integer(s.orderCount) : '—', money: false, unit: '单', caption: `平均客单价 ¥${s ? money(s.averageOrderAmount) : '—'}`, color: palette[5], icon: Tickets },
    { label: '售后申请', value: s ? integer(s.afterSaleCount) : '—', money: false, unit: '笔', caption: '关联期间原订单 · 不含已取消申请', color: palette[3], icon: RefreshLeft },
    { label: '当前库存总量', value: s ? integer(s.stockQuantity) : '—', money: false, unit: '件', caption: `可用库存 ${s ? integer(s.availableQuantity) : '—'} 件`, color: palette[4], icon: Box },
    { label: '库存预警', value: s ? integer(s.lowStockCount) : '—', money: false, unit: '组', caption: '按仓库 / SKU 核对可用库存下限', color: palette[2], icon: Warning },
  ]
})
function hasPage(path: string) { const route = router.resolve(path); return route.matched.length > 0 && route.name !== 'NotFound' }
const scopeQuery = computed(() => ({ ...(overview.value?.merchantId != null ? { merchantId: String(overview.value.merchantId) } : {}), ...(overview.value?.shopId != null ? { shopId: String(overview.value.shopId) } : {}) }))
const tasks = computed(() => [
  { label: '待发货订单', count: overview.value?.tasks.waitShip, icon: Van, color: palette[2], to: { path: '/sale/order_list', query: { ...scopeQuery.value, orderStatus: '1' } } },
  { label: '待实际入库', count: overview.value?.tasks.waitInbound, icon: Download, color: palette[0], to: { path: '/wms/stock_in_list' } },
  { label: '待实际出库', count: overview.value?.tasks.waitOutbound, icon: Upload, color: palette[1], to: { path: '/wms/stock_out_list' } },
  { label: '待处理售后', count: overview.value?.tasks.waitAfterSale, icon: RefreshLeft, color: palette[3], to: { path: '/sale/shop_refund' } },
].map(t => ({ ...t, value: t.count == null ? '—' : integer(t.count), available: hasPage(t.to.path) })))
const quickEntries = computed(() => [ ...(userStore.permissions.some(p => ['*:*:*','ai:sales:query','ai:finance:query','ai:risk:query'].includes(p)) ? [{ label: '经营资料汇总', path: '/management-agent' }] : []), ...(userStore.permissions.some(p => p === '*:*:*' || p === 'ai:risk:query') ? [{ label: '风险与售后资料助手', path: '/risk-agent' }] : []), ...(userStore.permissions.some(p => p === '*:*:*' || p === 'ai:marketing:query') ? [{ label: '广告与营销资料助手', path: '/marketing-agent' }] : []), ...(userStore.permissions.some(p => p === '*:*:*' || p === 'ai:product:query') ? [{ label: '商品运营助手', path: '/product-agent' }] : []), ...(userStore.permissions.some(p => p === '*:*:*' || p === 'ai:inventory:query') ? [{ label: '库存采购助手', path: '/inventory-agent' }] : []), ...(userStore.permissions.some(p => p === '*:*:*' || p === 'ai:sales:query') ? [{ label: '销售与订单助手', path: '/sales-agent' }] : []), ...(userStore.permissions.some(p => p === '*:*:*' || p === 'ai:finance:query') ? [{ label: '利润与财务资料助手', path: '/finance-agent' }] : []), { label: '订单查询', path: '/sale/order_list' }, { label: '采购订单', path: '/purchase/purchase_order' }, { label: '库存查询', path: '/wms/goods_stock' }, { label: '订单备货', path: '/wms/stocking' }, { label: '退款售后', path: '/sale/shop_refund' } ].filter(t => hasPage(t.path)))
const common = { backgroundColor: 'transparent', color: palette, textStyle: { fontFamily: 'Inter, Microsoft YaHei, sans-serif', color: '#606266' }, tooltip: { backgroundColor: '#ffffff', borderColor: '#e4e7ed', textStyle: { color: '#303133' }, confine: true }, animationDuration: 600 }
const splitLine = { lineStyle: { color: '#ebeef5', type: 'dashed' as const } }
function emptyOption(): EChartsCoreOption { return { ...common, graphic: { type: 'text', left: 'center', top: 'middle', style: { text: placeholder.value, fill: '#909399', fontSize: 13 } } } }
const trendOption = computed<EChartsCoreOption>(() => {
  const data = overview.value?.daily
  if (!data) return emptyOption()
  return { ...common, tooltip: { ...common.tooltip, trigger: 'axis' }, legend: { top: 8, itemWidth: 12, itemHeight: 6, textStyle: { color: '#606266', fontSize: 12 }, data: ['下单金额', '扣退款后净额', '订单量'] }, grid: { left: 14, right: 18, top: 53, bottom: 12, containLabel: true },
    xAxis: { type: 'category', data: data.map(d => d.date.slice(5)), axisLine: { lineStyle: { color: '#dcdfe6' } }, axisTick: { show: false }, axisLabel: { color: '#909399', fontSize: 12, hideOverlap: true } },
    yAxis: [{ type: 'value', name: '元', nameTextStyle: { color: '#909399' }, axisLabel: { color: '#909399', fontSize: 12 }, splitLine }, { type: 'value', name: '单', minInterval: 1, nameTextStyle: { color: '#909399' }, axisLabel: { color: '#909399', fontSize: 12 }, splitLine: { show: false } }],
    series: [ { name: '订单量', type: 'bar', yAxisIndex: 1, barMaxWidth: 16, data: data.map(d => d.orderCount), itemStyle: { color: 'rgba(64,158,255,.14)', borderRadius: [3, 3, 0, 0] } }, { name: '下单金额', type: 'line', smooth: .3, symbolSize: 5, data: data.map(d => d.grossAmount), itemStyle: { color: palette[0] }, lineStyle: { width: 3 }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(64,158,255,.16)' }, { offset: 1, color: 'rgba(64,158,255,0)' }] } } }, { name: '扣退款后净额', type: 'line', smooth: .3, symbolSize: 4, data: data.map(d => d.netAmount), itemStyle: { color: palette[1] }, lineStyle: { width: 2, type: 'dashed' } } ] }
})
const shopOption = computed<EChartsCoreOption>(() => {
  const rows = overview.value?.shops
  if (!rows?.length) return emptyOption()
  return { ...common, tooltip: { ...common.tooltip, trigger: 'axis', axisPointer: { type: 'shadow' } }, grid: { left: 12, right: 50, top: 14, bottom: 8, containLabel: true }, xAxis: { type: 'value', show: false, max: Number(rows[0]?.amount || 0) * 1.15 }, yAxis: { type: 'category', inverse: true, data: rows.map(r => r.name), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#606266', fontSize: 12, width: 90, overflow: 'truncate' } }, series: [{ name: '下单金额', type: 'bar', data: rows.map((r, i) => ({ value: r.amount, itemStyle: { color: i === 0 ? '#409eff' : '#a0cfff' } })), barWidth: 9, showBackground: true, backgroundStyle: { color: '#f2f6fc', borderRadius: 4 }, itemStyle: { borderRadius: 4 }, label: { show: true, position: 'right', color: '#606266', fontSize: 12 } }] }
})
function donut(rows: Slice[] | undefined, total: number | undefined, center: string): EChartsCoreOption {
  if (!rows?.length) return emptyOption()
  return { ...common, tooltip: { ...common.tooltip, trigger: 'item', formatter: '{b}<br/>{c} 笔（{d}%）' }, legend: { bottom: 5, left: 'center', itemWidth: 7, itemHeight: 7, itemGap: 10, textStyle: { color: '#606266', fontSize: 12 } }, graphic: [{ type: 'text', left: 'center', top: '30%', style: { text: integer(total || 0), fill: '#303133', fontSize: 32, fontWeight: 600, fontFamily: 'Consolas, monospace' } }, { type: 'text', left: 'center', top: '49%', style: { text: center, fill: '#909399', fontSize: 12 } }], series: [{ type: 'pie', radius: ['57%', '73%'], center: ['50%', '42%'], padAngle: 4, itemStyle: { borderRadius: 4 }, label: { show: false }, emphasis: { scaleSize: 4 }, data: rows.map(r => ({ name: r.name, value: r.value })) }] }
}
const orderOption = computed(() => donut(overview.value?.orderStatuses, summary.value?.orderCount, '期间订单 / 单'))
const afterOption = computed(() => donut(overview.value?.afterSaleTypes, summary.value?.afterSaleCount, '售后申请 / 笔'))
const warehouseOption = computed<EChartsCoreOption>(() => {
  const rows = overview.value?.warehouses
  if (!rows?.length) return emptyOption()
  return { ...common, tooltip: { ...common.tooltip, trigger: 'axis', axisPointer: { type: 'shadow' } }, legend: { top: 6, itemHeight: 7, itemWidth: 9, textStyle: { color: '#606266', fontSize: 12 } }, grid: { left: 12, right: 20, top: 43, bottom: 9, containLabel: true }, xAxis: { type: 'value', axisLabel: { color: '#909399', fontSize: 12 }, splitLine }, yAxis: { type: 'category', inverse: true, data: rows.map(r => r.name), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#606266', fontSize: 12, width: 100, overflow: 'truncate' } }, series: [{ name: '可用库存', type: 'bar', stack: 'stock', barWidth: 10, data: rows.map(r => r.available), itemStyle: { color: '#409eff', borderRadius: 3 } }, { name: '锁定库存', type: 'bar', stack: 'stock', data: rows.map(r => r.locked), itemStyle: { color: '#e6a23c', borderRadius: 3 } }] }
})
async function load(force = false) { const period = days.value; error.value = ''; try { await store.loadOverview(period, force) } catch (e) { if (period === days.value) error.value = e instanceof Error && /[\u4e00-\u9fff]/.test(e.message) ? e.message : '暂时无法获取最新统计，请稍后重试' } }
async function toggleFullscreen() { try { if (document.fullscreenElement) await document.exitFullscreen(); else await screen.value?.requestFullscreen() } catch { ElMessage.warning('当前浏览器未能进入全屏，可使用浏览器全屏模式') } }
function fullscreenChanged() { fullscreen.value = document.fullscreenElement === screen.value }
watch(days, () => load())
onMounted(() => { load(); timer = setInterval(() => { now.value = new Date() }, 1000); document.addEventListener('fullscreenchange', fullscreenChanged) })
onActivated(() => load())
onBeforeUnmount(() => { if (timer) clearInterval(timer); document.removeEventListener('fullscreenchange', fullscreenChanged) })
</script>

<style lang="scss" scoped>
.data-screen { --border: #e4e7ed; padding: 22px; border-radius: 6px; color: #303133; background: #f5f7fa; font-family: 'Helvetica Neue', 'Microsoft YaHei', sans-serif; min-width: 0; line-height: 1.4; box-sizing: border-box; &:fullscreen { width: 100vw; height: 100vh; overflow: auto; border-radius: 0; padding: 24px 32px; } button:focus-visible,a:focus-visible { outline: 2px solid #409eff; outline-offset: 3px; } }
.screen-header,.brand,.controls { display: flex; align-items: center; } .screen-header { justify-content: space-between; gap: 16px; } .brand { gap: 12px; } .brand-mark { display: grid; place-items: center; width: 38px; height: 38px; border: 1px solid #d9ecff; border-radius: 12px; color: #409eff; background: #ecf5ff; font-size: 25px; } .eyebrow { color: #909399; font-size: 9px; letter-spacing: 2px; margin-bottom: 6px; } h1 { margin: 0; font-size: 22px; letter-spacing: 0; font-weight: 600; span { margin-left: 14px; padding-left: 14px; border-left: 1px solid #dcdfe6; color: #606266; font-size: 12px; font-weight: 400; vertical-align: middle; } }
.controls { gap: 9px; time { font: 12px Consolas,monospace; color: #909399; margin-right: 12px; white-space: nowrap; } } .period-switch { display: flex; gap: 3px; padding: 3px; border: 1px solid #dcdfe6; border-radius: 7px; background: #ffffff; button { border: 0; border-radius: 5px; padding: 7px 13px; color: #606266; background: transparent; font-size: 12px; cursor: pointer; &.active { color: #409eff; background: #ecf5ff; } } } .icon-button { display: grid; place-items: center; width: 33px; height: 33px; border: 1px solid #dcdfe6; border-radius: 7px; background: #ffffff; color: #606266; cursor: pointer; &:hover { border-color: #a0cfff; color: #409eff; } &:disabled { opacity: .5; cursor: wait; } }
.scope-line { display: flex; align-items: center; gap: 12px; margin: 18px 0 16px; color: #909399; font-size: 11px; .updated { margin-left: auto; color: #909399; } } .scope-badge { color: #409eff; padding: 4px 9px; background: #ecf5ff; border-radius: 4px; }
.metrics-grid { display: grid; grid-template-columns: repeat(6,minmax(0,1fr)); gap: 12px; margin-bottom: 14px; } .metric { padding: 15px 17px; border: 1px solid var(--border); box-shadow: 0 1px 3px rgba(0, 0, 0, .025); border-radius: 8px; background: #fff; overflow: hidden; } .metric-top { display: flex; align-items: center; justify-content: space-between; color: #606266; font-size: 11px; .el-icon { color: var(--accent); font-size: 16px; } } .metric-value { display: flex; align-items: baseline; gap: 4px; margin: 12px 0 8px; font: 600 clamp(21px,2vw,34px) 'Helvetica Neue',Arial,sans-serif; letter-spacing: -.5px; white-space: nowrap; small { font: 11px 'Microsoft YaHei',sans-serif; color: #909399; } } .metric-caption { color: #909399; font-size: 10px; }
.visual-grid { display: grid; gap: 14px; margin-bottom: 14px; } .top-grid { grid-template-columns: minmax(0,1fr) minmax(0,1.6fr) minmax(0,.9fr); } .bottom-grid { grid-template-columns: minmax(0,1fr) minmax(0,1.4fr) minmax(0,1.1fr); } .panel { min-width: 0; border: 1px solid var(--border); background: #fff; border-radius: 9px; overflow: hidden; } .panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 14px 16px 0; h2 { margin: 0; padding-left: 9px; border-left: 3px solid #409eff; font-size: 13px; font-weight: 500; color: #303133; white-space: nowrap; } > span { font-size: 10px; color: #909399; } .live-tag { color: #67c23a; } } .chart-area,.products-list { height: clamp(210px,calc((100vh - 560px) / 2),300px); } .panel-foot { border-top: 1px solid #ebeef5; min-height: 32px; padding: 8px 15px; font-size: 10px; color: #909399; display: flex; align-items: center; gap: 5px; box-sizing: border-box; > span { margin-left: auto; } strong { color: #e6a23c; font: 12px Consolas,monospace; } }
.products-list { display: flex; flex-direction: column; justify-content: space-evenly; padding: 5px 17px; box-sizing: border-box; } .product-row { display: flex; align-items: center; gap: 10px; min-height: 35px; } .rank-number { font: 12px Consolas,monospace; color: #909399; &.podium { color: #e6a23c; } } .product-image { width: 30px; height: 30px; border-radius: 5px; flex-shrink: 0; background: #f2f6fc; display: grid; place-items: center; color: #909399; } .product-info { min-width: 0; flex: 1; } .product-name { color: #606266; font-size: 11px; white-space: nowrap; text-overflow: ellipsis; overflow: hidden; } .product-track { width: 90%; max-width: 170px; height: 3px; border-radius: 2px; background: #f2f6fc; margin-top: 9px; span { display: block; height: 100%; border-radius: inherit; background: #409eff; } } .product-sales { text-align: right; flex-shrink: 0; strong { display: block; font: 12px Consolas,monospace; color: #303133; } span { display: block; font-size: 10px; color: #909399; margin-top: 4px; } }
.tasks-strip { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); border: 1px solid var(--border); border-radius: 8px; background: #ffffff; margin-bottom: 15px; } .task-item { display: flex; align-items: center; gap: 12px; padding: 12px 17px; color: inherit; text-decoration: none; &:not(:last-child) { border-right: 1px solid var(--border); } &[href]:hover { background: #ecf5ff; } > .el-icon { font-size: 22px; } > strong { margin-left: auto; font: 24px Consolas,monospace; color: #909399; } > .task-arrow { font-size: 11px; color: #909399; } } .task-copy { display: flex; flex-direction: column; gap: 5px; span { color: #606266; font-size: 11px; } small { font-size: 9px; color: #909399; } }
footer { display: flex; align-items: center; gap: 10px; color: #909399; font-size: 10px; } .quick-links { display: flex; align-items: center; gap: 15px; flex-wrap: wrap; > span { color: #909399; } a { color: #606266; text-decoration: none; &:hover { color: #409eff; } } } .data-note { display: flex; align-items: center; gap: 5px; margin-left: auto; white-space: nowrap; cursor: help; } .empty-state { display: grid; place-items: center; height: 100%; color: #909399; font-size: 13px; } .error-banner { display: flex; align-items: center; gap: 8px; border: 1px solid #f3d19e; padding: 10px 14px; border-radius: 6px; background: #fdf6ec; color: #b88230; margin-bottom: 14px; font-size: 12px; button { background: transparent; border: 0; color: #409eff; cursor: pointer; margin-left: auto; } } .rotating { animation: rotate 1s linear infinite; } @keyframes rotate { to { transform: rotate(360deg); } }
@media (min-width: 1800px) { .metric-caption,.metric-top { font-size: 12px; } .panel-heading h2 { font-size: 15px; } .product-name { font-size: 12px; } .product-image { width: 38px; height: 38px; } }
@media (max-width: 1200px) { .metrics-grid { grid-template-columns: repeat(3,minmax(0,1fr)); } .controls time { display: none; } .top-grid { grid-template-columns: minmax(0,1fr) minmax(0,1.5fr); } .order-panel { grid-column: 1 / -1; } .order-panel .chart-area { height: 210px; } .bottom-grid { grid-template-columns: minmax(0,1fr) minmax(0,1.4fr); } .inventory-panel { grid-column: 1 / -1; } }
@media (max-width: 760px) { .data-screen { padding: 14px; border-radius: 8px; &:fullscreen { padding: 14px; } } .screen-header { align-items: flex-start; flex-direction: column; } .controls { width: 100%; } .period-switch { margin-right: auto; } .brand-mark { width: 36px; height: 36px; } h1 { font-size: 20px; } .scope-line { gap: 8px; flex-wrap: wrap; .updated { margin-left: 0; width: 100%; } } .metrics-grid { gap: 8px; grid-template-columns: repeat(2,minmax(0,1fr)); } .metric { padding: 12px; } .metric-value { font-size: 25px; } .metric-caption { min-height: 22px; font-size: 9px; line-height: 1.5; } .visual-grid { grid-template-columns: minmax(0,1fr); } .trend-panel { grid-row: 1; } .chart-area,.products-list { height: 260px; } .tasks-strip { grid-template-columns: repeat(2,minmax(0,1fr)); } .task-item { padding: 12px; gap: 8px; &:nth-child(2) { border-right: 0; } &:nth-child(-n+2) { border-bottom: 1px solid var(--border); } } .task-copy small { display: none; } .quick-links { gap: 10px; } }
@media (prefers-reduced-motion: reduce) { .rotating { animation: none; } }
</style>

