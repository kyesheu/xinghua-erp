<template>
  <el-dialog v-model="visible" :title="creating ? '新增本地售后' : '本地售后处理'" width="820px" append-to-body :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy">
    <el-alert title="本入口处理本地手工订单的传统SKU；退款需先在原收款渠道完成，本系统只登记结果。" type="info" :closable="false" />
    <el-form v-if="creating" label-width="120px" style="margin-top:16px">
      <el-form-item label="店铺"><el-select v-model="form.shopId" @change="clearOrder"><el-option v-for="s in shops" :key="s.id" :value="s.id" :label="s.name" /></el-select></el-form-item>
      <el-form-item label="原订单号"><el-input v-model="orderNum" @input="clearOrder" style="width:360px" /><el-button :loading="busy" @click="searchOrder">查询订单商品</el-button></el-form-item>
      <el-form-item label="原订单商品"><el-select v-model="form.orderItemId" placeholder="选择具体商品及规格" style="width:100%"><el-option v-for="i in items" :key="i.id" :value="i.id" :label="`${i.goodsTitle} / ${i.goodsSpec || '默认'} / 购买${i.quantity}件`" /></el-select></el-form-item>
      <el-form-item label="售后类型"><el-select v-model="form.type" @change="form.exchangeSkuId=null; form.refundAmount=0"><el-option v-for="(label,key) in types" :key="key" :label="label" :value="Number(key)" /></el-select></el-form-item>
      <el-form-item label="售后数量"><el-input-number v-model="form.quantity" :min="1" :precision="0" /></el-form-item>
      <el-form-item v-if="form.type===20" label="换出商品"><el-select v-model="form.exchangeSkuId" filterable remote :remote-method="searchSkus" placeholder="输入商品名称搜索内部SKU" style="width:100%"><el-option v-for="s in skus" :key="s.id" :value="s.id" :label="`${s.goodsName} / ${s.skuName} / ${s.skuCode}`" /></el-select></el-form-item>
      <el-form-item v-if="[1,10,11].includes(form.type)" label="退款金额（元）"><el-input-number v-model="form.refundAmount" :min="0" :precision="2" /></el-form-item>
      <el-form-item label="售后原因"><el-input v-model="form.reason" type="textarea" maxlength="200" /></el-form-item>
      <el-alert v-if="form.type===1" title="售前退款当前仅支持未分配、未取号的单商品整单退款；有发货任务的订单需先处理任务。" type="warning" :closable="false" />
    </el-form>
    <div v-else-if="detail.refund" v-loading="busy" style="margin-top:16px">
      <p>售后单：{{ detail.refund.refundNum }}</p>
      <p>{{ types[detail.refund.refundType] || detail.refund.refundType }} · {{ detail.refund.goodsName }} / {{ detail.refund.goodsSku }} · {{ detail.refund.quantity }} 件 · 退款 {{ Number(detail.refund.refundFee || 0).toFixed(2) }} 元</p>
      <el-alert v-if="detail.refund.status===1" title="申请已取消，未改动库存。" type="info" :closable="false" />
      <el-alert v-if="!detail.afterSale && detail.refund.status===0" title="点击处理后生成售后处理记录，不会直接修改库存。" type="info" :closable="false" />
      <el-form v-if="needsTasks" label-width="140px">
        <template v-if="[10,20].includes(detail.refund.refundType)">
          <el-form-item v-if="Object.keys(detail.returnSuppliers || {}).length" label="退货去向">
            <el-radio-group v-model="form.returnType"><el-radio :value="0">退回本地仓</el-radio><el-radio :value="300">退回供应商</el-radio></el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.returnType===300" label="退回供应商">
            <el-select v-model="form.returnSupplierId" placeholder="选择原发货供应商"><el-option v-for="(qty,id) in detail.returnSuppliers" :key="id" :value="Number(id)" :label="`${suppliers.find((s:any)=>String(s.id)===String(id))?.name || '供应商 #' + id}（已发 ${qty} 件）`" /></el-select>
          </el-form-item>
          <el-form-item v-else label="退回仓库"><el-select v-model="form.returnWarehouseId"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" /></el-select></el-form-item>
          <el-form-item label="退回物流公司"><el-input v-model="form.returnLogisticsCompany" maxlength="100" /></el-form-item>
          <el-form-item label="退回运单号"><el-input v-model="form.returnLogisticsCode" maxlength="100" /></el-form-item>
        </template>
        <template v-if="[20,80].includes(detail.refund.refundType)">
          <el-form-item label="发货方式"><el-radio-group v-model="form.reissueType"><el-radio :value="0">本地仓</el-radio><el-radio :value="300">供应商</el-radio></el-radio-group></el-form-item>
          <el-form-item v-if="form.reissueType===300" label="补发供应商"><el-select v-model="form.reissueSupplierId" filterable><el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="s.name" :disabled="s.isShipper!==1 || s.disable===1 || s.isDelete===1" /></el-select></el-form-item>
          <el-form-item v-else label="换出／补发仓库"><el-select v-model="form.warehouseId"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" /></el-select></el-form-item>
          <el-form-item label="收件人"><el-input v-model="form.receiverName" maxlength="50" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="form.receiverTel" maxlength="30" /></el-form-item>
          <el-form-item label="完整收货地址"><el-input v-model="form.receiverAddress" maxlength="200" /></el-form-item>
          <el-form-item v-if="form.reissueType!==300" label="发货快递"><el-select v-model="form.shippingCompanyId"><el-option v-for="l in carriers" :key="l.id" :value="l.id" :label="l.name" /></el-select></el-form-item>
          <el-form-item v-if="form.reissueType!==300" label="发货运单号"><el-input v-model="form.shippingNumber" maxlength="50" /></el-form-item>
        </template>
      </el-form>
      <template v-if="detail.afterSale">
        <p>处理进度：{{ detail.afterSale.status===10 ? '已办结' : detail.afterSale.status===0 ? '待确认仓库任务' : '仓库任务处理中' }}</p>
        <p v-for="i in detail.inbound" :key="i.id">退货入库单：{{ i.stockInNum }} · {{ stockStatus[i.status] }}（到入库管理按来源售后单号查询）</p>
        <el-alert v-if="detail.afterSale.returnType===300 && detail.afterSale.status!==10" title="请先线下核实供应商实际收货，办结时填写收货凭据或说明。" type="warning" :closable="false" />
        <p v-if="detail.afterSale.returnType===300">退回供应商：{{ suppliers.find((s:any)=>s.id===detail.afterSale.supplierId)?.name || '供应商 #' + detail.afterSale.supplierId }}；系统不自动通知供应商，也不会增加本地库存。</p>
        <div v-if="detail.supplierReissue">
          <p>供应商换货／补发任务：{{ detail.supplierReissue.sendStatus===2 ? '已登记发货' : '待登记发货' }}；不会自动通知供应商或扣本地库存。</p>
          <el-form v-if="detail.supplierReissue.sendStatus===0" label-width="130px">
            <el-form-item label="发货快递"><el-select v-model="form.shippingCompanyId"><el-option v-for="l in carriers" :key="l.id" :value="l.id" :label="l.name" /></el-select></el-form-item>
            <el-form-item label="发货运单号"><el-input v-model="form.shippingNumber" maxlength="50" /></el-form-item>
            <el-form-item><el-button type="primary" :loading="busy" @click="confirmSupplierShip">登记供应商发货</el-button></el-form-item>
          </el-form>
          <p v-else>物流：{{ detail.supplierReissue.waybillCompany }} {{ detail.supplierReissue.waybillCode }}</p>
        </div>
        <p v-if="detail.stockingId">换货／补发备货单 ID：{{ detail.stockingId }}（到订单备货按本售后单号查询并生成出库单）</p>
        <p v-for="o in detail.outbound" :key="o.id">出库单：{{ o.outNum }} · {{ stockStatus[o.status] }}（到出库管理执行实际出库）</p>
        <el-form v-if="detail.afterSale.status!==10" label-width="130px">
          <el-form-item v-if="detail.afterSale.returnType===300" label="供应商收货"><el-checkbox v-model="form.returnReceived">已确认供应商实际收到退货</el-checkbox></el-form-item>
          <el-form-item v-if="needsRefund" label="退款结果"><el-checkbox v-model="form.refundPaid">已在原收款渠道实际完成退款</el-checkbox></el-form-item>
          <el-form-item label="退款凭据／说明"><el-input v-model="form.remark" type="textarea" maxlength="200" placeholder="退款或确认供应商收货时填写凭据；本系统不自动打款或查询收货" /></el-form-item>
        </el-form>
      </template>
    </div>
    <template #footer>
      <el-button :disabled="busy" @click="visible=false">关闭</el-button>
      <el-button v-if="creating" type="primary" :loading="busy" @click="submitCreate">创建售后</el-button>
      <template v-else-if="detail.refund">
        <el-button :loading="busy" @click="refresh">刷新进度</el-button>
        <el-button v-if="!detail.afterSale && detail.refund.status===0" type="primary" :loading="busy" @click="process">处理售后</el-button>
        <el-button v-if="![1,4].includes(detail.refund.status) && (!detail.afterSale || detail.afterSale.status===0)" type="danger" :loading="busy" @click="cancel">取消申请</el-button>
        <el-button v-if="needsTasks" type="primary" :loading="busy" @click="confirmTasks">{{ form.returnType===300 ? '确认售后任务' : '确认并生成任务' }}</el-button>
        <el-button v-if="detail.afterSale && detail.afterSale.status!==10" type="success" :loading="busy" @click="complete">核对并办结</el-button>
      </template>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listShop } from '@/api/shop/shop'
import { listAllSupplier } from '@/api/goods/supplier'
import { listWarehouse } from '@/api/wms/warehouse'
import { listGoodsSpec } from '@/api/goods/goodsSpec'
import request from '@/utils/request'
import { findAfterSaleItems, createLocalAfterSale, getLocalAfterSale, processLocalAfterSale, confirmLocalAfterSale, confirmSupplierReissue, completeLocalAfterSale, cancelLocalAfterSale } from '@/api/afterSale/local'
const emit = defineEmits<{ (e:'changed'):void }>()
const visible=ref(false), creating=ref(false), busy=ref(false), orderNum=ref('')
const form=reactive<Record<string,any>>({}), detail=ref<any>({}), shops=ref<any[]>([]), items=ref<any[]>([]), skus=ref<any[]>([]), warehouses=ref<any[]>([]), carriers=ref<any[]>([]), suppliers=ref<any[]>([])
const types:Record<number,string>={1:'售前退款',11:'仅退款',10:'退货退款',20:'换货',80:'补发'}
const stockStatus:Record<number,string>={0:'待执行',1:'部分完成',2:'全部完成'}
const needsTasks=computed(()=>detail.value.afterSale?.status===0 && [10,20,80].includes(detail.value.refund?.refundType))
const needsRefund=computed(()=>[1,10,11].includes(detail.value.refund?.refundType) && detail.value.refund?.refundFee>0)
function reset(){for(const k of Object.keys(form))delete form[k];Object.assign(form,{shopId:null,orderItemId:null,type:10,quantity:1,refundAmount:0,exchangeSkuId:null,reason:'',refundPaid:false,returnType:0,returnSupplierId:null,returnReceived:false,reissueType:0,reissueSupplierId:null,remark:''});detail.value={};items.value=[];skus.value=[];orderNum.value=''}
async function run(action:()=>Promise<void>){if(busy.value)return;busy.value=true;try{await action()}catch{/* 统一请求拦截器显示错误，保留输入供修正 */}finally{busy.value=false}}
async function create(shopId?:number){if(busy.value)return;reset();creating.value=true;visible.value=true;await run(async()=>{const r:any=await listShop({});shops.value=r.rows||[];form.shopId=shopId??(shops.value.length===1?shops.value[0].id:null)})}
async function open(refundNum:string){if(busy.value)return;if(!refundNum?.startsWith('LOCAL-AF-')){ElMessage.info('平台售后请在原交易平台处理，当前本地入口不替代平台退款');return}reset();creating.value=false;visible.value=true;form.refundNum=refundNum;await run(async()=>{await refresh();const [w,l,s]:any[]=await Promise.all([listWarehouse({warehouseType:'LOCAL',status:1,pageSize:200}),request({url:'/api/erp-api/logistics/list',method:'get',params:{status:1,pageSize:200}}),listAllSupplier({})]);warehouses.value=(w.rows||[]).filter((i:any)=>i.warehouseType==='LOCAL'&&String(i.status)==='1');carriers.value=l.rows||[];suppliers.value=s.rows||[]})}
function clearOrder(){items.value=[];form.orderItemId=null}
async function searchOrder(){clearOrder();await run(async()=>{const r:any=await findAfterSaleItems({shopId:form.shopId,orderNum:orderNum.value});items.value=r.data||[];if(!items.value.length)ElMessage.warning('订单没有商品明细')})}
let skuVersion=0
async function searchSkus(keyword:string){const version=++skuVersion;try{const r:any=await listGoodsSpec({goodsName:keyword,inventoryMode:0,pageSize:30});if(version===skuVersion)skus.value=r.rows||[]}catch{}}
async function submitCreate(){if(!form.shopId||!form.orderItemId||!form.reason?.trim()){ElMessage.warning('请选择原订单商品并填写售后原因');return}await run(async()=>{await createLocalAfterSale({...form});ElMessage.success('售后已创建，可在列表点击处理继续');visible.value=false;emit('changed')})}
async function refresh(){const r:any=await getLocalAfterSale(form.refundNum);detail.value=r.data||{}}
async function process(){await run(async()=>{await processLocalAfterSale(form.refundNum);await refresh();emit('changed');ElMessage.success('已生成售后处理记录')})}
async function confirmTasks(){await run(async()=>{await confirmLocalAfterSale({...form});await refresh();emit('changed');ElMessage.success('已登记售后任务，请核对收货及发货进度后办结')})}
async function confirmSupplierShip(){await run(async()=>{await confirmSupplierReissue({...form});await refresh();emit('changed');ElMessage.success('已登记供应商发货')})}
async function complete(){await run(async()=>{await ElMessageBox.confirm('请核对实际入库、出库及退款结果。办结不执行实际支付。','确认办结',{confirmButtonText:'确认办结',cancelButtonText:'取消'});await completeLocalAfterSale({...form});await refresh();emit('changed');ElMessage.success('售后已办结')})}
async function cancel(){await run(async()=>{await ElMessageBox.confirm('仅取消尚未生成仓库任务的申请，不会执行退款或改动库存。','取消售后申请',{confirmButtonText:'确认取消',cancelButtonText:'返回'});await cancelLocalAfterSale(form.refundNum);await refresh();emit('changed');ElMessage.success('申请已取消')})}
defineExpose({create,open})
</script>
