<template>
  <div class="page">
    <h1>商品运营助手</h1><p>集中查看商品规格、库存、供应商和店铺商品关联，供运营人员核对。</p>
    <el-alert title="资料来自当前系统记录；缺少关联或库存记录不代表不存在。文案和关键词仅为草稿，须人工审核；不会自动发布。" type="info" :closable="false" />
    <el-card class="section" shadow="never">
      <template #header><div class="header"><strong>商品资料</strong><el-button :loading="loading" @click="load(page)">刷新</el-button></div></template>
      <el-form inline @submit.prevent="search"><el-form-item label="商品搜索"><el-input v-model.trim="keyword" maxlength="100" clearable placeholder="商品名称、SKU 编码或规格" style="width:260px" /></el-form-item><el-form-item><el-button type="primary" native-type="submit">搜索</el-button><el-button @click="reset">重置</el-button></el-form-item></el-form>
      <el-alert v-if="error" title="商品资料加载失败，请核对权限、账号范围及服务状态后重试。" type="error" :closable="false" />
      <p v-if="overview" class="muted">{{ overview.scope }} · 共 {{ overview.total }} 个 SKU。库存为授权范围内良品汇总；异常或缺失需核实。</p>
      <el-table v-if="overview" v-loading="loading" :data="overview.records" row-key="skuId" @expand-change="expand" empty-text="当前范围没有商品">
        <el-table-column type="expand"><template #default="{row}"><div class="detail" v-loading="!!busy[row.skuId]">
          <div class="header"><strong>{{ row.goodsName }} · 关联资料</strong><el-button :loading="!!busy[row.skuId]" @click="loadDetail(row.skuId)">刷新资料</el-button></div>
          <el-alert v-if="errors[row.skuId]" title="关联资料查询失败，请重试；失败不能视为空记录。" type="error" :closable="false" />
          <template v-if="details[row.skuId]">
            <h4>各仓库存</h4><p class="muted">{{ details[row.skuId].inventory.limitation }}</p>
            <el-table :data="details[row.skuId].inventory.records" empty-text="没有匹配库存记录，需核实"><el-table-column prop="warehouseId" label="仓库 ID"/><el-table-column prop="merchantId" label="商户 ID"/><el-table-column prop="shopId" label="店铺 ID"/><el-table-column prop="quantity" label="总库存"/><el-table-column prop="lockedQuantity" label="锁定"/><el-table-column prop="availableQuantity" label="可用"/><el-table-column prop="stockStatus" label="库存类型代码"/><el-table-column prop="updateTime" label="更新时间" min-width="170"/></el-table>
            <h4>供应商关联</h4><p v-if="!details[row.skuId].suppliersVisible" class="muted">店铺账号不展示无法按店铺归属的供应商资料。</p><template v-else><p v-if="details[row.skuId].suppliersTruncated" class="muted">关联超过 200 条，仅显示前 200 条。</p><el-table :data="details[row.skuId].suppliers" empty-text="未找到关联，需核实"><el-table-column prop="supplierId" label="供应商 ID"/><el-table-column prop="supplierName" label="供应商"/><el-table-column prop="productName" label="供应商商品"/><el-table-column prop="skuCode" label="供应商 SKU"/><el-table-column prop="standard" label="规格"/><el-table-column prop="status" label="状态代码"/></el-table></template>
            <h4>Listing 文案与关键词草稿</h4><p class="muted">仅将商品名称、SKU 编码、规格及下方输入发送给已配置的模型；生成可能产生费用。关键词不是商品事实；仅校验已配置的规则，平台全部要求仍须人工确认。</p>
            <el-form inline @submit.prevent="generate(row)"><el-form-item label="语言"><el-input v-model.trim="language" :disabled="draftBusy" maxlength="40" placeholder="例如中文或英语" /></el-form-item><el-form-item label="目标受众（选填）"><el-input v-model.trim="audience" :disabled="draftBusy" maxlength="200" placeholder="仅填写已确认信息" /></el-form-item><el-form-item label="目标站点（选填）"><el-input v-model.trim="site" :disabled="draftBusy" maxlength="100" placeholder="例如站点及地区" /></el-form-item><el-form-item label="确认关键词（选填）"><el-input v-model.trim="confirmedKeywords" :disabled="draftBusy" maxlength="500" placeholder="已确认可用的关键词" /></el-form-item><el-form-item><el-button type="primary" native-type="submit" :loading="draftBusy" :disabled="!language">生成待审核草稿</el-button></el-form-item></el-form>
            <el-alert v-if="draftError && draftSku === row.skuId" :title="draftError" type="warning" :closable="false" />
            <div v-if="draftText && draftSku === row.skuId"><p class="muted">待人工审核，可编辑后复制；不会保存或发布。</p><el-input v-model="draftText" type="textarea" :rows="10" @input="ruleCheck = null" /><p v-if="!ruleCheck">草稿已编辑，原站点规则和尺寸核对结果已失效，请人工重新核对。</p><p v-else-if="ruleCheck?.status === 'not_configured'">该站点未配置规则，尚未执行站点规则检查。</p><p v-else-if="ruleCheck">规则版本 {{ ruleCheck.version }} · 来源 {{ ruleCheck.source }}；{{ ruleCheck.status === 'issues' ? ruleCheck.issues.join('；') : '已通过已配置项目的检查' }}。编辑草稿后结果不再适用，仍须人工核对其他要求。</p><p v-if="ruleCheck && measurementWarnings.length">尺寸、重量及容量待核对项（含无依据数值或原始参数遗漏）：{{ measurementWarnings.join("、") }}，请核对；单位换算和语义仍需人工确认。</p><p>模型生成时的引用（编辑后可能不再对应，须人工逐项核对）：</p><p v-for="(item, index) in draftEvidence" :key="index">{{ item.text }}：{{ item.evidence.map(e => `${e.field}「${e.quote}」`).join("；") }}</p></div>
            <h4>店铺商品关联</h4><p v-if="details[row.skuId].shopsTruncated" class="muted">关联超过 200 条，仅显示前 200 条。</p><el-table :data="details[row.skuId].shops" empty-text="未找到关联，需核实"><el-table-column prop="shopId" label="店铺 ID"/><el-table-column prop="shopName" label="店铺"/><el-table-column prop="productTitle" label="店铺商品" min-width="160"/><el-table-column prop="productId" label="平台商品 ID"/><el-table-column prop="platformSkuId" label="平台 SKU ID"/><el-table-column prop="skuName" label="店铺规格"/><el-table-column prop="status" label="状态代码"/><el-table-column prop="updateOn" label="更新时间" min-width="170"/></el-table>
          </template>
        </div></template></el-table-column>
        <el-table-column prop="goodsName" label="商品名称" min-width="200"/><el-table-column prop="skuCode" label="SKU 编码" min-width="150"/><el-table-column prop="skuName" label="规格" min-width="130"/><el-table-column prop="risk" label="库存状态" min-width="130"/><el-table-column label="可用良品" min-width="110"><template #default="{row}">{{ row.risk === '库存数据异常' ? '数据异常' : row.goodRows ? row.availableQuantity : '待核实' }}</template></el-table-column>
      </el-table>
      <el-pagination v-if="overview && overview.total > overview.size" class="section" layout="total, prev, pager, next" :total="overview.total" :page-size="overview.size" :current-page="page" @current-change="load" />
    </el-card>
  </div>
</template>
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import request from '@/utils/request'
type Row = {skuId:string;goodsName:string;goodRows:number;risk:string;availableQuantity:number}
type Overview = {scope:string;total:number;size:number;records:Row[]}
type Detail = {inventory:{limitation:string;records:Record<string,unknown>[]};suppliersVisible:boolean;suppliersTruncated:boolean;shopsTruncated:boolean;suppliers:Record<string,unknown>[];shops:Record<string,unknown>[]}
const overview=ref<Overview|null>(null), page=ref(1), loading=ref(false), error=ref(false)
const details=ref<Record<string,Detail>>({}), busy=ref<Record<string,boolean>>({}), errors=ref<Record<string,boolean>>({})
const keyword=ref(''), appliedKeyword=ref('')
const language=ref('中文'), audience=ref(''), site=ref(''), confirmedKeywords=ref(''), draftBusy=ref(false), draftText=ref(''), draftError=ref(''), draftSku=ref('')
const draftEvidence=ref<{text:string;evidence:{field:string;quote:string}[]}[]>([])
const ruleCheck=ref<{status:string;version:string|null;source:string|null;issues:string[]}|null>(null)
const measurementWarnings=ref<string[]>([])
let draftSequence=0
async function generate(row:Row){const id=++draftSequence;draftSku.value=row.skuId;draftText.value='';draftEvidence.value=[];ruleCheck.value=null;measurementWarnings.value=[];draftError.value='';draftBusy.value=true;try{const response=await request<any,{data:{modelStatus:string;draft:string|null;evidence?:{text:string;evidence:{field:string;quote:string}[]}[];ruleCheck?:{status:string;version:string|null;source:string|null;issues:string[]};measurementWarnings?:string[]}}>({url:'/api/erp-api/ai-data/product/draft',method:'post',timeout:35000,data:{skuId:row.skuId,language:language.value,audience:audience.value,site:site.value,confirmedKeywords:confirmedKeywords.value}});if(id!==draftSequence)return;if(response.data.modelStatus==='ok' && response.data.draft){draftText.value=response.data.draft;draftEvidence.value=response.data.evidence||[];ruleCheck.value=response.data.ruleCheck||null;measurementWarnings.value=response.data.measurementWarnings||[]}else draftError.value=response.data.modelStatus==='disabled'?'模型未启用，未生成草稿。':response.data.modelStatus==='invalid'?'草稿引用的商品事实无法核对，请重试或补充商品资料。':response.data.modelStatus==='rules_invalid'?'站点规则配置无效，请联系管理员修正。':'模型不可用或未返回内容，请重试。'}catch{if(id===draftSequence)draftError.value='草稿生成失败，请核对权限、AI 服务和模型配置。'}finally{if(id===draftSequence)draftBusy.value=false}}
function search(){appliedKeyword.value=keyword.value;void load(1)}
function reset(){keyword.value='';search()}
let sequence=0; const detailSequence:Record<string,number>={}
async function load(p:number){const id=++sequence;++draftSequence;draftBusy.value=false;draftText.value='';draftError.value='';page.value=p;loading.value=true;error.value=false;overview.value=null;details.value={};busy.value={};errors.value={};try{const response=await request<any,{data:Overview}>({url:'/api/erp-api/ai-data/product/overview',params:{page:p,size:50,keyword:appliedKeyword.value}});if(id===sequence)overview.value=response.data}catch{if(id===sequence)error.value=true}finally{if(id===sequence)loading.value=false}}
async function loadDetail(skuId:string){const id=(detailSequence[skuId]||0)+1;detailSequence[skuId]=id;const generation=sequence;busy.value[skuId]=true;errors.value[skuId]=false;delete details.value[skuId];try{const response=await request<any,{data:Detail}>({url:'/api/erp-api/ai-data/product/dossier',params:{skuId}});if(generation===sequence && id===detailSequence[skuId])details.value[skuId]=response.data}catch{if(generation===sequence && id===detailSequence[skuId])errors.value[skuId]=true}finally{if(generation===sequence && id===detailSequence[skuId])busy.value[skuId]=false}}
function expand(row:Row, expanded:Row[]){if(expanded.some(item=>item.skuId===row.skuId) && !details.value[row.skuId] && !busy.value[row.skuId])void loadDetail(row.skuId)}
onMounted(()=>void load(1))
</script>
<style scoped>.page{max-width:1500px;margin:auto;padding:24px;color:#263344}.section{margin-top:20px}.header{display:flex;justify-content:space-between;align-items:center}.detail{padding:16px 32px}.muted{color:#738096;font-size:13px}h4{margin:24px 0 8px}</style>
