<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryFormRef" :model="queryParams" size="small" :inline="true" label-width="108px">
      <el-form-item label="售后单号" prop="refundNum">
        <el-input v-model="queryParams.refundNum" placeholder="请输入售后单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="订单号" prop="orderNum">
        <el-input v-model="queryParams.orderNum" placeholder="请输入订单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable @change="handleQuery">
          <el-option label="未办结" value="0" />
          <el-option label="已关闭" value="1" />
          <el-option label="已办结" value="4" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
        <el-button size="small" @click="resetQuery"><el-icon><Refresh /></el-icon>重置</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="售后单号" align="left" prop="refundNum" width="200" />
      <el-table-column label="订单号" align="left" prop="orderNum" width="200" />
      <el-table-column label="售后类型" align="center" prop="refundType" width="100">
        <template #default="scope">
          <el-tag>{{ typeNames[scope.row.refundType] || scope.row.refundType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="商品" align="left" prop="goodsName" min-width="200" />
      <el-table-column label="数量" align="center" prop="quantity" width="60" />
      <el-table-column label="状态" align="center" prop="status" width="120">
        <template #default="scope">
          <el-tag v-if="platformStatus[scope.row.status]" :type="platformStatus[scope.row.status].type">{{ platformStatus[scope.row.status].label }}</el-tag>
          <el-tag v-else-if="scope.row.status === 4" type="success">已办结</el-tag>
          <el-tag v-else-if="scope.row.status === 1" type="info">已关闭</el-tag>
          <el-tag v-else-if="scope.row.hasProcessing === 1" type="warning">处理中</el-tag>
          <el-tag v-else-if="scope.row.status === 0" type="info">待处理</el-tag>
          <el-tag v-else type="warning">代码 {{ scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200">
        <template #default="scope">
          <el-button size="small" type="primary" link @click="handleProcess(scope.row)">处理／查看进度</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    <LocalAfterSaleDialog ref="localDialog" @changed="getList" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listReturned } from '@/api/refund/refund'
import Pagination from '@/components/Pagination/index.vue'
import LocalAfterSaleDialog from '@/components/LocalAfterSaleDialog/index.vue'
const localDialog = ref<InstanceType<typeof LocalAfterSaleDialog>>()
const typeNames:Record<number,string>={1:'售前退款',11:'仅退款',10:'退货退款',20:'换货',80:'补发'}
// o_refund.status 混用两套词表：本地售后 0/1/4，平台同步 10001-10094。
// 平台码不映射会全部显示成「待处理」（如 10010 退款完成、14000 已拒绝），这是错的。
const platformStatus: Record<number, { label: string; type: 'success' | 'info' | 'warning' | 'danger' }> = {
  10005: { label: '等待卖家处理', type: 'warning' },
  10090: { label: '退款中', type: 'warning' },
  10010: { label: '退款完成', type: 'success' },
  10011: { label: '已关闭(平台)', type: 'info' },
  10020: { label: '售后成功', type: 'success' },
  10021: { label: '售后失败', type: 'danger' },
  14000: { label: '已拒绝', type: 'danger' },
  10091: { label: '换货成功', type: 'success' },
  10092: { label: '换货失败', type: 'danger' },
  10093: { label: '维修关闭', type: 'info' },
  10094: { label: '维修成功', type: 'success' },
}

const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref<any[]>([])
const queryParams = reactive({ pageNum: 1, pageSize: 10, refundNum: null, orderNum: null, status: null })

function getList() {
  loading.value = true
  listReturned(queryParams).then((res: any) => {
    dataList.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}
function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() { handleQuery() }
function handleProcess(row: any) { localDialog.value?.open(row.refundNum) }
onMounted(() => { getList() })
</script>
