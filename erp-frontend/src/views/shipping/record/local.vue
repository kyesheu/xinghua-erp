<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryFormRef" :model="queryParams" size="small" :inline="true" label-width="68px">
      <el-form-item label="订单号" prop="orderNum">
        <el-input v-model="queryParams.orderNum" placeholder="请输入订单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="快递单号" prop="waybillCode">
        <el-input v-model="queryParams.waybillCode" placeholder="请输入快递单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="店铺" prop="shopId">
        <el-select v-model="queryParams.shopId" placeholder="请选择店铺" filterable clearable @change="handleQuery">
          <el-option v-for="item in shopList" :key="item.id" :label="item.name" :value="item.id">
            <span style="float:left">{{ item.name }}</span>
            <span style="float:right;color:#8492a6;font-size:13px" v-if="item.type===100">淘宝天猫</span>
            <span style="float:right;color:#8492a6;font-size:13px" v-else-if="item.type===300">拼多多</span>
            <span style="float:right;color:#8492a6;font-size:13px" v-else-if="item.type===400">抖店</span>
            <span style="float:right;color:#8492a6;font-size:13px" v-else-if="item.type===200">京东POP</span>
            <span style="float:right;color:#8492a6;font-size:13px" v-else-if="item.type===500">微信小店</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="发货状态" prop="sendStatus">
        <el-select v-model="queryParams.sendStatus" placeholder="发货状态" clearable @change="handleQuery">
          <el-option v-for="item in statusList" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
        <el-button size="small" @click="resetQuery"><el-icon><Refresh /></el-icon>重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <RightToolbar :showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="shippingList">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="订单号" align="left" width="200">
        <template #default="scope">
          <el-button size="small" type="primary" link @click="handleDetail(scope.row)">{{ scope.row.orderNum }}</el-button>
          <el-icon class="copy-icon" @click="copyText(scope.row.orderNum)"><DocumentCopy /></el-icon><br />
          <el-tag type="info">{{ shopList.find((x:any)=>x.id===scope.row.shopId)?.name || (scope.row.shopType===0?'总部销售订单':'未知平台') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="商品" width="450">
        <template #default="scope">
          <div v-for="(item,index) in scope.row.itemList" :key="item.id || index" style="display:flex;align-items:center;padding-right:20px;margin-bottom:8px">
            <ImagePreview :src="item.goodsImg" :width="40" :height="40" />
            <div style="margin-left:10px">
              <div style="width:350px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis">{{ item.goodsName }}</div>
              <div><span style="color:#5a5e66;font-size:11px">规格：</span><el-tag size="small">{{ item.skuName }}</el-tag>&nbsp;<span>数量：<el-tag size="small">x{{ item.quantity }}</el-tag></span></div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="发货仓库" prop="warehouseName" width="120" />
      <el-table-column label="快递单号" align="center" prop="waybillCode" width="150">
        <template #default="scope">
          {{ scope.row.waybillCode }}
          <el-button v-if="scope.row.waybillCode" size="small" type="primary" link @click="handleTrack(scope.row)">跟踪</el-button>
        </template>
      </el-table-column>
      <el-table-column label="物流公司" align="center" prop="waybillCompany" width="120" />
      <el-table-column label="发货状态" align="center" prop="sendStatus" width="80">
        <template #default="scope">
          <el-tag v-if="scope.row.sendStatus===-1" type="info">已撤销</el-tag>
          <el-tag v-else-if="scope.row.sendStatus===0">待发货</el-tag>
          <el-tag v-else-if="scope.row.sendStatus===1" type="warning">部分发货</el-tag>
          <el-tag v-else-if="scope.row.sendStatus===2" type="success">已发货</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发货时间" align="center" prop="sendTime" width="160">
        <template #default="scope">{{ parseTime(scope.row.shippingTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right"><template #default="scope">
        <el-button v-if="scope.row.sendStatus===0 && scope.row.orderType===0" type="primary" link @click="openShip(scope.row)">登记发货</el-button>
        <el-button v-if="scope.row.sendStatus===0 && scope.row.orderType===0" type="danger" link @click="cancelTask(scope.row)">撤销分配</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog title="登记本地发货" v-model="shipOpen" width="500px" append-to-body>
      <p>登记发货后请继续在备货页面生成出库单；实际出库时才扣库存。</p>
      <el-form label-width="90px"><el-form-item label="物流公司"><el-select v-model="shipForm.logisticsCompany" filterable><el-option v-for="item in logistics" :key="item.id" :value="item.id" :label="item.name" /></el-select></el-form-item>
      <el-form-item label="运单号"><el-input v-model="shipForm.waybillCode" /></el-form-item></el-form>
      <template #footer><el-button @click="shipOpen=false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitShip">确认</el-button></template>
    </el-dialog>

    <pagination v-show="total>0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, DocumentCopy } from '@element-plus/icons-vue'
import { listShop } from '@/api/shop/shop'
import { listShipRecord } from '@/api/shipping/shipRecord'
import { confirmLocalTask, cancelLocalTask } from '@/api/shipping/shipOrder'
import { listLogistics } from '@/api/shipping/logisticsLibrary'
import { wuliuguiji } from '@/api/shipping/logisticsTracking'
import { parseTime } from '@/utils/zhijian'
import Pagination from '@/components/Pagination/index.vue'
import RightToolbar from '@/components/RightToolbar/index.vue'
import ImagePreview from '@/components/ImagePreview/index.vue'

const loading=ref(true);const showSearch=ref(true);const total=ref(0);const shippingList=ref<any[]>([]);const shopList=ref<any[]>([])
const statusList=[{value:'',label:'全部'},{value:'-1',label:'已撤销'},{value:'0',label:'待发货'},{value:'1',label:'部分发货'},{value:'2',label:'已发货'}]
const shipOpen=ref(false);const submitting=ref(false);const logistics=ref<any[]>([])
const shipForm=reactive({id:null as number|null,logisticsCompany:null as number|null,waybillCode:''})
const queryParams=reactive({pageNum:1,pageSize:10,orderNum:null as string|null,waybillCode:null as string|null,shopId:null as number|null,sendStatus:null as string|null,type:0})
function getList(){loading.value=true;listShipRecord(queryParams).then((res:any)=>{shippingList.value=res.rows||[];total.value=res.total||0;loading.value=false}).catch(()=>{loading.value=false})}
function handleQuery(){queryParams.pageNum=1;getList()}
function resetQuery(){queryParams.orderNum=null;queryParams.waybillCode=null;queryParams.shopId=null;queryParams.sendStatus=null;handleQuery()}
function handleTrack(row:any){wuliuguiji({waybillCode:row.waybillCode,logisticsCompany:row.logisticsCompany}).then((res:any)=>{const info=res.data||res.rows||[];ElMessage.info(info.length>0?JSON.stringify(info.slice(0,3)):'暂无物流信息')})}
function openShip(row:any){shipForm.id=row.id;shipForm.logisticsCompany=null;shipForm.waybillCode='';shipOpen.value=true}
async function submitShip(){
  if(!shipForm.logisticsCompany || !shipForm.waybillCode.trim()){ElMessage.warning('请选择物流并填写运单号');return}
  if(submitting.value)return
  submitting.value=true
  try{await confirmLocalTask(shipForm);ElMessage.success('已登记本地发货，请继续备货出库');shipOpen.value=false;getList()}finally{submitting.value=false}
}
async function cancelTask(row:any){
  try{await ElMessageBox.confirm('撤销后可重新分配，确定撤销吗？','撤销本地任务',{type:'warning'})}catch{return}
  await cancelLocalTask(row.id);ElMessage.success('已撤销本地任务');getList()
}
function handleDetail(row:any){ElMessage.info('订单号: '+row.orderNum)}
function copyText(text:string){navigator.clipboard.writeText(text).then(()=>ElMessage.success('复制成功')).catch(()=>ElMessage.warning('不支持自动复制'))}
onMounted(()=>{listShop({}).then((res:any)=>{shopList.value=res.rows||[]});listLogistics({pageNum:1,pageSize:1000,status:1}).then((res:any)=>{logistics.value=res.rows||[]});getList()})
</script>
<style scoped>.copy-icon{cursor:pointer;margin-left:4px;color:#409eff}</style>