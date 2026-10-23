<template>
  <div class="finance-assistant">
    <h1>利润与财务资料助手</h1><p>集中查看支付、退款和已登记流水，供人工核对；资料差额不等于利润。</p>
    <el-form inline @submit.prevent="load"><el-form-item label="原单统计周期"><el-select v-model="days" style="width:200px" @change="load"><el-option :value="7" label="近7个完整自然日" /><el-option :value="30" label="近30个完整自然日" /></el-select></el-form-item><el-button :loading="loading" @click="load">刷新资料</el-button></el-form>
    <el-alert v-if="error" title="财务资料查询失败，请核对财务权限、账号绑定和服务状态后重试；失败不代表零收入或零费用。" type="error" :closable="false" />
    <template v-if="result">
      <p class="muted">{{ result.scope }} · {{ time(result.start) }} 至 {{ time(result.endExclusive) }}（不含结束时间） · 查询于 {{ time(result.retrievedAt) }}</p>
      <el-alert :title="result.limitation" type="info" :closable="false" />
      <el-card class="section" shadow="never"><template #header><strong>原订单及退款资料</strong></template><el-table :data="metrics"><el-table-column prop="label" label="指标" min-width="260" /><el-table-column prop="value" label="数值" /></el-table></el-card>
      <el-card class="section" shadow="never"><template #header><strong>结算利润（已审核结算单口径）</strong></template>
        <template v-if="result.settlement && result.settlement.available && result.settlement.settledOrders>0">
          <p>窗口内已审核结算单 {{ result.settlement.settledOrders }} 笔，结算利润 {{ money(result.settlement.profit) }}（订单实际金额减采购成本、运费及登记费用，不含退款调整；未结算订单不含在内，与支付减退款口径不同不可相加）。</p>
          <el-table :data="settlementRows"><el-table-column prop="label" label="构成" min-width="240" /><el-table-column prop="value" label="金额" /></el-table>
          <ul>
            <li>窗口内未结算的已付订单 {{ result.settlement.unsettledPaidOrders }} 笔，不含在上述利润中，不代表零利润。</li>
            <li>成本为零的结算单 {{ result.settlement.zeroCostSettled }} 笔（通常无出库批次成本记录），需人工核实。</li>
            <li>费用项仅含订单已登记并审核通过的费用单，未登记费用不代表为零。</li>
          </ul>
        </template>
        <template v-else><p>窗口内暂无已审核结算单，结算利润不可用，不代表零利润。</p></template>
        <p v-if="result.missingInputs && result.missingInputs.length">待完善的资料口径：</p><ul><li v-for="item in result.missingInputs" :key="item">{{ item }}</li></ul>
      </el-card>
      <el-card class="section" shadow="never">
        <template #header><strong>负利润订单（已审核结算单口径）</strong><el-button v-if="loss && loss.available && loss.orders.length" class="export-btn" size="small" :loading="exporting" @click="exportLoss">导出明细 CSV</el-button></template>
        <template v-if="loss && loss.available">
          <p>窗口内识别负利润订单 {{ loss.count }} 笔<span v-if="loss.truncated">（已按利润从低到高截断至5000笔，超出部分未包含）</span>；诱因为确定性阈值判定，可多命中并列，不代表唯一根因。</p>
          <el-alert v-if="!loss.orders.length" title="窗口内没有负利润订单；只代表当前结算单中没有利润为负的记录，不代表没有亏损。" type="info" :closable="false" />
          <template v-else>
            <el-table class="section-inner" :data="lossCauseSummary" empty-text="无诱因分类"><el-table-column prop="cause" label="诱因" min-width="160" /><el-table-column prop="count" label="订单数" /></el-table>
            <el-table class="section-inner" :data="loss.orders">
              <el-table-column prop="orderNo" label="订单号" min-width="140" />
              <el-table-column prop="salesAmount" label="销售额" />
              <el-table-column prop="purchaseCost" label="采购成本" />
              <el-table-column prop="shippingFee" label="运费" />
              <el-table-column prop="platformFee" label="平台扣点" />
              <el-table-column prop="marketingFee" label="营销" />
              <el-table-column prop="otherFee" label="其他" />
              <el-table-column prop="totalCost" label="总成本" />
              <el-table-column prop="profit" label="利润" />
              <el-table-column label="诱因" min-width="180"><template #default="scope">{{ scope.row.causes.join('；') || '无' }}</template></el-table-column>
            </el-table>
          </template>
          <el-alert class="section-inner" :title="loss.limitation" type="info" :closable="false" />
        </template>
        <p v-else class="muted">结算单表尚未建立，无法识别负利润订单；不代表没有亏损订单，也不会自动建表或补结算。</p>
      </el-card>
      <el-card class="section" shadow="never"><template #header><strong>待核实资料</strong></template><el-table :data="quality"><el-table-column prop="label" label="问题及统计口径" min-width="280" /><el-table-column prop="value" label="记录数" /></el-table><p class="muted">0仅代表当前查询未发现该类记录，不证明业务资料完整。</p></el-card>
      <el-card class="section" shadow="never"><template #header><strong>独立展示的已登记财务流水</strong></template><p>{{ result.ledger.limitation }}</p><template v-if="result.ledger.available"><p>窗口内登记 {{ result.ledger.entries }} 条，符合金额检查的收入合计 {{ money(result.ledger.income) }}，支出合计 {{ money(result.ledger.expense) }}；异常金额 {{ result.ledger.invalidRows }} 条。</p><el-table :data="result.ledger.accounts" empty-text="当前范围及记账窗口没有符合条件的流水"><el-table-column label="科目"><template #default="scope">{{ scope.row.accountType || '科目未设置' }}</template></el-table-column><el-table-column prop="entries" label="记录数" /><el-table-column label="已登记收入"><template #default="scope">{{ money(scope.row.income) }}</template></el-table-column><el-table-column label="已登记支出"><template #default="scope">{{ money(scope.row.expense) }}</template></el-table-column></el-table></template><p v-else class="muted">流水资料不可用，不能按零费用计算。</p></el-card>
    </template>
    <p v-else-if="loading">正在读取授权范围内的财务资料…</p>
  </div>
</template>
<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {getFinanceOverview,getLossOrders,exportLossOrders} from '@/api/ai/financeAgent'
import type {FinanceOverview,LossOrders} from '@/api/ai/financeAgent'
const days=ref(30);const loading=ref(false);const error=ref(false);const result=ref<FinanceOverview|null>(null)
const loss=ref<LossOrders|null>(null);const exporting=ref(false)
let sequence=0
async function load(){const id=++sequence;loading.value=true;error.value=false;result.value=null;loss.value=null
  try{const [overview,lossData]=await Promise.all([getFinanceOverview(days.value),getLossOrders(days.value)])
    if(id===sequence){result.value=overview.data;loss.value=lossData.data}}
  catch{if(id===sequence)error.value=true}finally{if(id===sequence)loading.value=false}}
async function exportLoss(){exporting.value=true
  try{const blob=await exportLossOrders(days.value)
    const file=blob instanceof Blob?blob:new Blob([blob as any])
    const link=document.createElement('a');link.href=URL.createObjectURL(file)
    link.download=`loss-orders-${days.value}d-${new Date().toISOString().slice(0,10)}.csv`;link.click();URL.revokeObjectURL(link.href)}
  finally{exporting.value=false}}
const lossCauseSummary=computed(()=>loss.value&&loss.value.available?Object.entries(loss.value.causeCounts).map(([cause,count])=>({cause,count})):[])
const metrics=computed(()=>result.value?[
  {label:'本期原单中符合支付口径的订单数',value:result.value.orders.count},
  {label:'原订单支付金额（含取消、售后等指定状态）',value:money(result.value.orders.payment)},
  {label:'当前已完成的匹配退款笔数（含异常金额）',value:result.value.refunds.count},
  {label:'匹配退款金额（异常金额未计入）',value:money(result.value.refunds.amount)},
  {label:'支付减登记退款（非利润、未换汇）',value:money(result.value.paymentLessRefund)},
]:[])
const quality=computed(()=>result.value?[
  {label:'原单窗口中负支付金额订单',value:result.value.orders.negativePaymentRows},
  {label:'匹配原单退款中金额缺失或为负的记录',value:result.value.refunds.invalidRows},
  {label:'退款申请窗口中未匹配原单的已完成退款',value:result.value.refunds.unmatchedRows},
  {label:'退款申请窗口中匹配多张原单的已完成退款',value:result.value.refunds.ambiguousRows},
]:[])
const settlementRows=computed(()=>result.value&&result.value.settlement?[
  {label:'结算销售额（订单实际金额，非支付口径）',value:money(result.value.settlement.salesAmount)},
  {label:'采购成本（出库批次登记）',value:money(result.value.settlement.purchaseCost)},
  {label:'发货费用',value:money(result.value.settlement.shippingFee)},
  {label:'平台扣点（登记费用单）',value:money(result.value.settlement.platformFee)},
  {label:'营销费用（登记费用单）',value:money(result.value.settlement.marketingFee)},
  {label:'其他费用（登记费用单）',value:money(result.value.settlement.otherFee)},
  {label:'总成本',value:money(result.value.settlement.totalCost)},
  {label:'结算利润',value:money(result.value.settlement.profit)},
]:[])
function money(value:number|null){return value===null?'未提供':new Intl.NumberFormat('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}).format(value)}
function time(value:string){return value.replace('T',' ').split('.')[0]}
onMounted(()=>{void load()})
</script>
<style scoped>
.finance-assistant{max-width:1200px;margin:0 auto;padding:24px;color:#263344}.section{margin-top:20px}.muted{color:#738096;font-size:13px}li{margin:10px 0}
.section-inner{margin-top:14px}.export-btn{float:right}
</style>
