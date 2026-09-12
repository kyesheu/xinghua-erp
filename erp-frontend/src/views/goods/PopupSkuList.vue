<template>
  <el-dialog v-model="dialogVisible" :title="supplierId ? '选择供应商商品' : '选择商品'">
    <el-alert v-if="supplierId" title="仅列出当前供应商已关联的SKU，可直接勾选；下方输入框用于搜索。" type="info" :closable="false" style="margin-bottom: 12px" />
    <el-form :model="queryParams" ref="queryFormRef" size="small" :inline="true" label-width="120px">
      <el-form-item label="商品名称" prop="goodsName">
        <el-input v-model="queryParams.goodsName" placeholder="请输入商品名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品ID" prop="goodsId">
        <el-input v-model="queryParams.goodsId" placeholder="请输入商品ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品编码" prop="goodsNum">
        <el-input v-model="queryParams.goodsNum" placeholder="请输入商品编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商品SKUID" prop="id">
        <el-input v-model="queryParams.id" placeholder="请输入商品SKUID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="SKU编码" prop="skuCode">
        <el-input v-model="queryParams.skuCode" placeholder="请输入SKU编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商户" prop="merchantId" v-if="!isMerchant && !isShop">
        <el-select v-model="queryParams.merchantId" placeholder="请选择商户" @change="handleQuery">
          <el-option v-for="item in merchantList" :key="item.id" :label="item.name" :value="item.id"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="商品归属" prop="name" v-if="isMerchant || isShop">
        <el-select v-model="queryParams.ownerId" @change="handleQuery" placeholder="请选择商品归属">
          <el-option v-for="item in ownerList" :key="item.id" :label="item.name" :value="item.id"></el-option>
        </el-select>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-if="btn === 1"
          type="primary"
          :disabled="multiple"
          @click="sendDataToParent"
        ><el-icon><Plus /></el-icon>确认添加</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-if="btn === 2"
          type="primary"
          :disabled="single"
          @click="sendDataToParent"
        >确认选中</el-button>
      </el-col>
    </el-row>

    <el-table ref="tableRef" v-loading="loading" :data="goodsSpecList" @selection-change="handleSelectionChange"
      :empty-text="supplierId ? '没有匹配的供货SKU，请检查搜索条件；尚未关联商品时，请先在供应商产品中维护供货关系' : '暂无商品'">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="规格ID" align="center" prop="id" width="80" />
      <el-table-column label="商品ID" align="center" prop="goodsId" width="80" />
      <el-table-column label="图片" align="center" prop="colorImage" width="60">
        <template #default="scope">
          <image-preview :src="scope.row.colorImage" :width="35" :height="35" />
        </template>
      </el-table-column>
      <el-table-column label="商品名称" align="left" prop="goodsName" />
      <el-table-column label="Sku规格" align="center" prop="skuName" />
      <el-table-column label="商品编码" align="center" prop="goodsNum" />
      <el-table-column label="Sku编码" align="center" prop="skuCode" />
      <el-table-column v-if="supplierId" label="供应商报价（元）" align="center" prop="supplierPrice">
        <template #default="scope">{{ scope.row.supplierPrice ?? '未报价' }}</template>
      </el-table-column>
      <el-table-column label="商户" align="center" prop="merchantId">
        <template #default="scope">
          <el-tag size="small" v-if="scope.row.merchantId === 0">总部</el-tag>
          <el-tag size="small" v-else>{{ merchantList.find((x: any) => x.id == scope.row.merchantId)?.name }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { listGoodsSpec } from '@/api/goods/goodsSpec'
import { listMerchant } from '@/api/shop/merchant'
import Pagination from '@/components/Pagination/index.vue'
import ImagePreview from '@/components/ImagePreview/index.vue'
import type { FormInstance } from 'element-plus'

const props = withDefaults(defineProps<{
  btn?: number
  merchantId?: number | null
  supplierId?: number | null
}>(), {
  btn: 1,
  merchantId: null
})

const emit = defineEmits<{
  (e: 'data-from-select', data: any[]): void
}>()

const queryFormRef = ref<FormInstance>()
const tableRef = ref()
let requestVersion = 0
const dialogVisible = ref(false)
const isMerchant = ref(false)
const isShop = ref(false)
const loading = ref(true)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const goodsSpecList = ref<any[]>([])
const merchantList = ref<any[]>([])
const ownerList = ref<any[]>([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  goodsId: null,
  id: null,
  goodsNum: null,
  skuCode: null,
  merchantId: null,
  ownerId: '',
  goodsName: null
})

function openDialog() {
  queryParams.pageNum = 1
  queryParams.goodsId = null
  queryParams.id = null
  queryParams.goodsNum = null
  queryParams.skuCode = null
  queryParams.goodsName = null
  queryParams.merchantId = null
  listMerchant({ pageNum: 1, pageSize: 100 }).then((resp: any) => {
    merchantList.value = resp.rows || []
  })
  getList()
  dialogVisible.value = true
}

function sendDataToParent() {
  if (!ids.value || ids.value.length === 0) {
    ElMessage.error('请选择商品')
    return
  }
  const filteredList = goodsSpecList.value.filter((item: any) => ids.value.includes(item.id))
  emit('data-from-select', filteredList)
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  queryParams.goodsId = null
  queryParams.id = null
  queryParams.goodsNum = null
  queryParams.skuCode = null
  dialogVisible.value = false
}

function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.id)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function getList() {
  const version = ++requestVersion
  tableRef.value?.clearSelection()
  ids.value = []
  single.value = true
  multiple.value = true
  goodsSpecList.value = []
  loading.value = true
  listGoodsSpec({ ...queryParams, supplierId: props.supplierId }).then((response: any) => {
    if (version !== requestVersion) return
    goodsSpecList.value = response.rows || []
    total.value = response.total || 0
    loading.value = false
  }).catch(() => {
    if (version !== requestVersion) return
    total.value = 0
    loading.value = false
  })
}

watch(() => props.supplierId, () => {
  ++requestVersion
  tableRef.value?.clearSelection()
  goodsSpecList.value = []
  total.value = 0
  if (dialogVisible.value) handleQuery()
})

defineExpose({ openDialog })
</script>

<style scoped lang="scss">
</style>
