<template>
  <div class="sales-assistant">
    <h1>销售与订单助手</h1>
    <p>集中查看当前授权范围的销售与订单资料，由人判断和处理。</p>
    <el-form inline @submit.prevent="load">
      <el-form-item label="统计周期"><el-select v-model="days" style="width:200px" @change="load"><el-option :value="7" label="近7个完整自然日" /><el-option :value="30" label="近30个完整自然日" /></el-select></el-form-item>
      <el-button :loading="loading" @click="load">刷新资料</el-button>
    </el-form>
    <el-alert v-if="error" title="销售资料查询失败，请检查账号权限、业务绑定和服务后重试；失败不代表零销售。" type="error" :closable="false" />
    <div v-if="result" v-loading="loading">
      <p class="muted">{{ result.scope }} · {{ time(result.start) }} 至 {{ time(result.endExclusive) }}（不含结束时间） · 查询于 {{ time(result.retrievedAt) }}</p>
      <el-alert :title="result.limitation" type="info" :closable="false" />
      <el-card class="section" shadow="never"><template #header><strong>本期与上期</strong></template>
        <el-table :data="metrics"><el-table-column prop="label" label="指标" /><el-table-column prop="current" label="本期" /><el-table-column prop="previous" label="上期同等天数" /></el-table>
      </el-card>
      <el-alert v-if="result.quality.unlinkedRows || result.quality.invalidQuantityRows" class="section" :title="`有效支付订单中有 ${result.quality.unlinkedRows} 条明细未关联内部SKU，${result.quality.invalidQuantityRows} 条数量缺失或不为正；两类可能重叠，请核对商品榜口径。`" type="warning" :closable="false" />
      <el-card class="section" shadow="never"><template #header><strong>每日有效支付订单趋势</strong></template><el-table :data="result.trend"><el-table-column prop="day" label="日期" /><el-table-column prop="orders" label="订单数" /><el-table-column prop="payment" label="支付金额（未扣退款）" /></el-table></el-card>
      <el-card class="section" shadow="never"><template #header><strong>窗口内全部订单的当前状态</strong></template><el-table :data="result.statuses" empty-text="当前范围及窗口没有订单"><el-table-column label="当前状态"><template #default="scope">{{ statusName(scope.row.status) }}</template></el-table-column><el-table-column prop="orders" label="订单数" /></el-table></el-card>
      <el-card class="section" shadow="never"><template #header><strong>店铺支付金额前20项</strong></template><el-table :data="result.shops" empty-text="没有匹配的有效支付订单"><el-table-column label="店铺 ID"><template #default="scope">{{ scope.row.shopId ?? '归属缺失，待核实' }}</template></el-table-column><el-table-column prop="orders" label="有效支付订单数" /><el-table-column prop="payment" label="支付金额（未扣退款）" /></el-table></el-card>
      <el-card class="section" shadow="never"><template #header><strong>内部SKU销量前20项</strong></template><el-table :data="result.products" empty-text="没有已关联内部SKU的有效销量"><el-table-column prop="skuId" label="内部 SKU ID" /><el-table-column prop="title" label="订单商品标题参考" /><el-table-column prop="quantity" label="数量（未扣退款）" /></el-table></el-card>
      <el-card class="section" shadow="never">
        <template #header><strong>SKU 表现排行</strong><el-button class="export-btn" size="small" :loading="exporting" :disabled="!sku" @click="exportSku">导出明细 CSV</el-button></template>
        <template v-if="sku">
          <p class="muted">窗口内共 {{ sku.skuCount }} 个内部SKU；销量中位数 {{ sku.medianQuantity ?? '不可用' }}，退款率中位数 {{ sku.medianRefundRate === null ? '不可用' : percent(sku.medianRefundRate) }}。四象限为启发式分类，非平台标准。</p>
          <el-alert v-if="sku.unlinkedRefundRows" :title="`有 ${sku.unlinkedRefundRows} 笔已完成退款未关联或SKU无效，未参与分摊；请核对退款登记。`" type="warning" :closable="false" />
          <p>集中度（窗口内销售额占比）：TOP5 {{ shareText(sku.topShares.top5) }}、TOP10 {{ shareText(sku.topShares.top10) }}、TOP20 {{ shareText(sku.topShares.top20) }}；分布与集中度基于全量 {{ sku.skuCount }} 行计算，下表仅展示前 {{ sku.rows.length }} 行。</p>
          <el-table class="section-inner" :data="quadrantSummary" empty-text="商品数量不足，未做四象限分类"><el-table-column label="象限" min-width="140"><template #default="scope">{{ quadrantName(scope.row.key) }}</template></el-table-column><el-table-column prop="count" label="商品数" /><el-table-column label="销售额合计"><template #default="scope">{{ scope.row.sales }}</template></el-table-column></el-table>
          <el-table class="section-inner" :data="sku.rows" empty-text="当前范围及窗口没有已关联内部SKU的有效销量">
            <el-table-column prop="skuId" label="内部 SKU ID" min-width="110" />
            <el-table-column label="商品标题" min-width="160"><template #default="scope">{{ scope.row.title ?? '标题缺失，待核实' }}</template></el-table-column>
            <el-table-column prop="paidOrders" label="付款订单数" />
            <el-table-column prop="quantity" label="销量" />
            <el-table-column prop="salesAmount" label="销售额" />
            <el-table-column prop="refundOrders" label="退款订单数" />
            <el-table-column prop="refundAmount" label="退款金额" />
            <el-table-column label="退款率"><template #default="scope">{{ scope.row.refundRate === null ? '不可用' : percent(scope.row.refundRate) }}</template></el-table-column>
            <el-table-column label="四象限"><template #default="scope">{{ quadrantName(scope.row.quadrant) }}</template></el-table-column>
          </el-table>
          <el-alert class="section-inner" :title="sku.limitation" type="info" :closable="false" />
        </template>
        <p v-else class="muted">SKU 表现资料加载中或不可用，不代表没有商品销量。</p>
      </el-card>
    </div>
    <p v-else-if="loading" class="muted">正在读取授权范围内的资料…</p>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getSalesOverview, getSkuPerformance, exportSkuPerformance } from '@/api/ai/salesAgent'
import type { SalesOverview, SkuPerformance } from '@/api/ai/salesAgent'
const days=ref(30); const loading=ref(false); const error=ref(false); const result=ref<SalesOverview | null>(null)
const sku=ref<SkuPerformance | null>(null); const exporting=ref(false)
let sequence=0
async function load() {
  const current=++sequence
  loading.value=true; error.value=false; result.value=null; sku.value=null
  try {
    const [overview,performance]=await Promise.all([getSalesOverview(days.value),getSkuPerformance(days.value)])
    if(current===sequence) { result.value=overview.data; sku.value=performance.data }
  }
  catch { if(current===sequence) error.value=true }
  finally { if(current===sequence) loading.value=false }
}
async function exportSku() {
  exporting.value=true
  try {
    const blob=await exportSkuPerformance(days.value)
    downloadBlob(blob instanceof Blob ? blob : new Blob([blob as any]), `sku-performance-${days.value}d-${new Date().toISOString().slice(0,10)}.csv`)
  }
  finally { exporting.value=false }
}
function downloadBlob(blob:Blob,filename:string) {
  const link=document.createElement('a')
  link.href=URL.createObjectURL(blob)
  link.download=filename
  link.click()
  URL.revokeObjectURL(link.href)
}
const metrics=computed(()=>result.value ? [
  {label:'全部状态订单数',current:result.value.current.orders,previous:result.value.previous.orders},
  {label:'有效支付订单数',current:result.value.current.paidOrders,previous:result.value.previous.paidOrders},
  {label:'支付金额（未扣退款、未换汇）',current:result.value.current.payment,previous:result.value.previous.payment},
] : [])
const quadrantSummary=computed(()=>sku.value ? Object.entries(sku.value.quadrantCounts).map(([key,count])=>({
  key,count,sales:new Intl.NumberFormat('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}).format(sku.value!.quadrantSales[key] ?? 0),
})) : [])
function quadrantName(value:string|null) {
  return ({highSalesLowRefund:'高销量低退款',highSalesHighRefund:'高销量高退款',lowSalesLowRefund:'低销量低退款',lowSalesHighRefund:'低销量高退款'} as Record<string,string>)[value ?? ''] || '未分类'
}
function shareText(value:number|null) { return value===null?'不可用（窗口内销售额为0）':`${(value/10).toFixed(1)}%` }
function percent(value:number) { return `${(value*100).toFixed(2)}%` }
function time(value:string) { return value.replace('T',' ').split('.')[0] }
function statusName(value:number | null) {
  return ({0:'新订单',1:'待发货',2:'已发货',3:'已完成',11:'已取消',12:'退款中',13:'已关闭',21:'待付款',22:'锁定',29:'删除',31:'售后中',101:'部分发货'} as Record<number,string>)[value ?? -1] || `待核实（${value ?? '未设置'}）`
}
onMounted(()=>{void load()})
</script>
<style scoped>
.sales-assistant { max-width:1200px; margin:0 auto; padding:24px; color:#263344; }
.section { margin-top:20px; }.muted { color:#738096; font-size:13px; }
.section-inner { margin-top:14px; }
.export-btn { float:right; }
</style>
