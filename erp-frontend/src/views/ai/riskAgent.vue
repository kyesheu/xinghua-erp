<template>
  <div class="page">
    <h1>风险与售后资料助手</h1>
    <p>集中核对当前授权范围的系统内部退款及售后记录，由人工判断处理。</p>
    <el-alert v-if="overview?.limitation" :title="overview?.limitation" type="info" :closable="false" />

    <el-card class="section" shadow="never">
      <template #header>
        <div class="header">
          <strong>智能体汇总</strong>
          <el-button :loading="overviewLoading" @click="refresh">刷新</el-button>
        </div>
      </template>
      <el-alert v-if="overviewError" title="汇总加载失败，请核对权限及服务状态后重试；失败不代表没有风险。" type="error" :closable="false" />
      <template v-if="overview">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="授权范围">{{ overview.scope }}</el-descriptions-item>
          <el-descriptions-item label="售后记录总数">{{ overview.total }}</el-descriptions-item>
          <el-descriptions-item label="未关闭">{{ overview.open }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="overview.findings" empty-text="未发现规则化异常项" max-height="360">
          <el-table-column prop="title" label="异常项" min-width="180" />
          <el-table-column prop="level" label="级别" width="100" />
          <el-table-column prop="count" label="条数" width="90" />
          <el-table-column prop="advice" label="建议" min-width="240" />
        </el-table>
      </template>
      <el-form class="analysis" @submit.prevent="analyze">
        <el-form-item>
          <el-checkbox v-model="useModel" :disabled="analysisLoading" @change="clearAnalysis">同时生成模型解读（可能产生费用，问题及汇总资料将发送给模型服务）</el-checkbox>
        </el-form-item>
        <el-form-item v-if="useModel">
          <el-input v-model="question" type="textarea" maxlength="500" show-word-limit placeholder="可填写希望解读的问题，请勿输入敏感个人信息" :disabled="analysisLoading" @input="clearAnalysis" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="analysisLoading" :disabled="analysisLoading" @click="analyze">生成汇总</el-button>
        </el-form-item>
      </el-form>
      <p v-if="analysisError">汇总生成失败，请重试</p>
      <template v-if="analysis">
        <p v-if="analysis.modelStatus === 'disabled'">模型未启用，仅显示规则汇总。</p>
        <p v-else-if="analysis.modelStatus === 'unavailable'">模型暂不可用，仅显示规则汇总。</p>
        <div v-if="analysis.explanation">
          <h3>模型解读（须人工核实）</h3>
          <p class="explanation">{{ analysis.explanation }}</p>
        </div>
        <div v-for="(section, index) in analysis.sections" :key="index" class="section-item">
          <p class="section-title">{{ section.name || sourceLabel(section.source) }}</p>
          <p>{{ section.summary }}</p>
          <p class="muted">限制：{{ section.limitation }}</p>
        </div>
      </template>
    </el-card>

    <el-card class="section" shadow="never">
      <template #header><div class="header"><strong>内部退款及售后记录</strong><el-button :loading="loading" @click="load(page)">刷新</el-button></div></template>
      <p>来源：内部退款表，按申请记录创建时间倒序；金额、状态和类型为原始记录，请结合原单核实。</p>
      <el-alert v-if="error" title="售后资料加载失败，请检查权限、账号绑定和服务状态；失败不代表没有记录。" type="error" :closable="false" />
      <el-table v-if="data" max-height="600" v-loading="loading" :data="data.records" empty-text="当前范围没有记录">
        <el-table-column prop="refundNum" label="售后单号" min-width="160" />
        <el-table-column prop="orderNum" label="订单号" min-width="160" />
        <el-table-column prop="merchantId" label="商户 ID" />
        <el-table-column prop="shopId" label="店铺 ID" />
        <el-table-column prop="goodsName" label="商品" min-width="160" />
        <el-table-column prop="goodsSku" label="规格" min-width="120" />
        <el-table-column prop="quantity" label="数量" />
        <el-table-column prop="refundFee" label="申请金额" />
        <el-table-column prop="refundType" label="类型代码" />
        <el-table-column prop="status" label="售后状态代码" />
        <el-table-column prop="erpStatus" label="ERP状态代码" />
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column prop="updateTime" label="更新时间" min-width="170" />
      </el-table>
      <el-pagination v-if="data && data.total>50" layout="total, prev, pager, next" :total="data.total" :page-size="50" :current-page="page" @current-change="load" />
    </el-card>
  </div>
</template>
<script setup lang="ts">
import {onMounted,ref} from 'vue'
import request from '@/utils/request'
import {analyzeRisk, getRiskOverview} from '@/api/ai/riskAgent'
import type {RiskAnalysis, RiskOverview} from '@/api/ai/riskAgent'
import {sourceLabel} from '@/api/ai/agentSource'
const data=ref<{total:number;records:Record<string,unknown>[]}|null>(null),page=ref(1),loading=ref(false),error=ref(false)
let sequence=0
async function load(p:number){const id=++sequence;page.value=p;loading.value=true;error.value=false;data.value=null;try{const r=await request<any,{data:{total:number;records:Record<string,unknown>[]}}>({url:'/api/erp-api/ai-data/risk/refunds',params:{page:p}});if(id===sequence)data.value=r.data}catch{if(id===sequence)error.value=true}finally{if(id===sequence)loading.value=false}}
const overview=ref<RiskOverview|null>(null),overviewLoading=ref(false),overviewError=ref(false)
let overviewSequence=0
async function loadOverview(){const id=++overviewSequence;overviewLoading.value=true;overviewError.value=false;try{const r=await getRiskOverview();if(id===overviewSequence)overview.value=r.data}catch{if(id===overviewSequence){overview.value=null;overviewError.value=true}}finally{if(id===overviewSequence)overviewLoading.value=false}}
const useModel=ref(false),question=ref(''),analysis=ref<RiskAnalysis|null>(null),analysisLoading=ref(false),analysisError=ref(false)
let analysisSequence=0
function clearAnalysis(){analysisSequence++;analysis.value=null;analysisError.value=false;analysisLoading.value=false}
async function analyze(){const id=++analysisSequence;analysis.value=null;analysisError.value=false;analysisLoading.value=true;try{const r=await analyzeRisk({question:useModel.value?question.value:'',useModel:useModel.value});if(id===analysisSequence)analysis.value=r.data}catch{if(id===analysisSequence)analysisError.value=true}finally{if(id===analysisSequence)analysisLoading.value=false}}
function refresh(){clearAnalysis();void Promise.all([loadOverview(),load(page.value)])}
onMounted(()=>{void loadOverview();void load(1)})
</script>
<style scoped>.page{max-width:1500px;margin:auto;padding:24px;color:#263344}.section{margin-top:20px}.header{display:flex;justify-content:space-between;align-items:center}.analysis{margin-top:16px}.limitation,.muted{color:#5c6b7a;line-height:1.7}.section-item{border-top:1px solid #ebeef5;padding-top:12px;margin-top:12px}.section-title{font-weight:600;color:#3a4656}.explanation{white-space:pre-wrap;line-height:1.8}</style>
