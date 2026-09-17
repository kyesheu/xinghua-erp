<template>
  <div class="app-container">
    <el-form inline :model="query">
      <el-form-item label="售后单号"><el-input v-model="query.refundNum" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item label="订单号"><el-input v-model="query.orderNum" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item label="类型"><el-select v-model="query.type" clearable style="width:160px" @change="search"><el-option v-for="(name,key) in types" :key="key" :label="name" :value="Number(key)" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable style="width:160px" @change="search"><el-option label="待确认任务" :value="0" /><el-option label="仓库任务处理中" :value="1" /><el-option label="已办结" :value="10" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search">搜索</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-alert title="确认售后只生成仓库任务；实际入库、出库后再核对办结。平台售后及原有外部记录不在本地入口登记完成。" type="info" :closable="false" style="margin-bottom:16px" />
    <el-table :data="rows" v-loading="loading">
      <el-table-column label="售后单号" prop="refundNum" min-width="230" />
      <el-table-column label="原订单号" prop="orderNum" min-width="170" />
      <el-table-column label="类型" width="110"><template #default="s">{{ types[s.row.type] || s.row.type }}</template></el-table-column>
      <el-table-column label="商品" prop="title" min-width="180" />
      <el-table-column label="规格" prop="skuInfo" min-width="120" />
      <el-table-column label="数量" prop="quantity" width="80" />
      <el-table-column label="处理结果" prop="result" min-width="180" />
      <el-table-column label="状态" width="150"><template #default="s">{{ states[s.row.status] || s.row.status }}</template></el-table-column>
      <el-table-column label="操作" width="160"><template #default="s"><el-button type="primary" link @click="localDialog?.open(s.row.refundNum)">处理／查看进度</el-button></template></el-table-column>
    </el-table>
    <Pagination v-show="total>0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    <LocalAfterSaleDialog ref="localDialog" @changed="load" />
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { list } from '@/api/afterSale/afterSale'
import Pagination from '@/components/Pagination/index.vue'
import LocalAfterSaleDialog from '@/components/LocalAfterSaleDialog/index.vue'
const localDialog=ref<InstanceType<typeof LocalAfterSaleDialog>>()
const rows=ref<any[]>([]),loading=ref(false),total=ref(0)
const types:Record<number,string>={1:'售前退款',11:'仅退款',10:'退货退款',20:'换货',80:'补发',30:'维修',99:'拦截'}
const states:Record<number,string>={0:'待确认任务',1:'仓库任务处理中',2:'已收货',10:'已办结'}
const query=reactive({pageNum:1,pageSize:10,refundNum:'',orderNum:'',type:null as number|null,status:null as number|null})
async function load(){loading.value=true;try{const r:any=await list({...query});rows.value=r.rows||[];total.value=r.total||0}finally{loading.value=false}}
function search(){query.pageNum=1;load()}
function reset(){Object.assign(query,{pageNum:1,refundNum:'',orderNum:'',type:null,status:null});load()}
onMounted(load)
</script>
