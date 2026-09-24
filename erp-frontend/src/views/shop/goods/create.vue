<template>
  <div class="app-container">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="店铺" prop="shopId">
        <el-select v-model="form.shopId" placeholder="请选择店铺" filterable :loading="shopLoading" style="width: 360px">
          <el-option v-for="shop in shopList" :key="shop.id" :label="shop.name" :value="String(shop.id)" />
        </el-select>
        <span v-if="!shopLoading && !shopList.length" class="form-tip">暂无店铺，请先到渠道管理创建店铺。</span>
      </el-form-item>
      <el-form-item label="商品名称" prop="goodsName">
        <el-input v-model="form.goodsName" placeholder="选择商品库 SKU 后自动带出，可修改" maxlength="255" />
      </el-form-item>
      <el-form-item label="商品编码" prop="goodsNum">
        <el-input v-model="form.goodsNum" placeholder="请输入商品编码" maxlength="255" />
      </el-form-item>
      <el-form-item label="平台商品ID" prop="productId">
        <el-input v-model="form.productId" placeholder="选填，不填则自动生成本地商品 ID" maxlength="50" />
      </el-form-item>
      <el-form-item label="商品规格" prop="skuList">
        <div class="sku-editor">
          <el-button type="primary" plain @click="popupRef?.openDialog()">选择商品库 SKU</el-button>
          <el-table :data="form.skuList" border class="sku-table">
            <el-table-column label="商品库 SKU" min-width="180">
              <template #default="{ row }">{{ row.skuCode }}<br />{{ row.skuName }}</template>
            </el-table-column>
            <el-table-column label="平台SKU ID（选填）" min-width="180">
              <template #default="{ row }"><el-input v-model="row.skuId" placeholder="不填自动生成" maxlength="50" /></template>
            </el-table-column>
            <el-table-column label="售价（元）" width="165">
              <template #default="{ row }">
                <el-input-number v-model="row.price" :min="0" :max="21474836.47" :precision="2" controls-position="right" style="width: 140px" />
              </template>
            </el-table-column>
            <el-table-column label="店铺库存" width="155">
              <template #default="{ row }">
                <el-input-number v-model="row.stockNum" :min="0" :max="2147483647" :precision="0" controls-position="right" style="width: 130px" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ $index }"><el-button type="danger" link @click="form.skuList.splice($index, 1)">移除</el-button></template>
            </el-table-column>
          </el-table>
          <div class="form-tip">同一商品可选择多个规格。店铺库存用于渠道商品资料，仓库实物库存仍通过出入库维护。</div>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
        <el-button @click="router.back()">取消</el-button>
      </el-form-item>
    </el-form>
    <PopupSkuList ref="popupRef" @data-from-select="addSkus" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addShopGoods } from '@/api/shop/goods'
import { listShop } from '@/api/shop/shop'
import PopupSkuList from '@/views/goods/PopupSkuList.vue'
import type { FormInstance, FormRules } from 'element-plus'

interface ShopOption {
  id: number | string
  name: string
  type: number
  merchantId?: number | string
}

interface SelectedSku {
  erpGoodsSkuId: string
  erpGoodsId: string
  skuId: string
  outerSkuId: string
  skuName: string
  skuCode: string
  img: string
  price: number | undefined
  stockNum: number | undefined
}

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const popupRef = ref<InstanceType<typeof PopupSkuList>>()
const shopList = ref<ShopOption[]>([])
const shopLoading = ref(false)
const submitting = ref(false)
const form = reactive({
  shopId: '',
  goodsName: '',
  goodsNum: '',
  goodsImg: '',
  productId: '',
  skuList: [] as SelectedSku[],
})
const rules: FormRules = {
  shopId: [{ required: true, message: '请选择店铺', trigger: 'change' }],
  goodsName: [{ required: true, whitespace: true, message: '请输入商品名称', trigger: 'blur' }],
  skuList: [{ type: 'array', required: true, min: 1, message: '请至少选择一个商品库 SKU', trigger: 'change' }],
}

onMounted(async () => {
  shopLoading.value = true
  try {
    const rawType = String(route.query.shopType || '')
    const query = /^\d+$/.test(rawType) ? { type: Number(rawType) } : {}
    const response: any = await listShop(query)
    shopList.value = response.rows || []
    const selected = shopList.value.find(shop => String(shop.id) === String(route.query.shopId))
    if (selected) form.shopId = String(selected.id)
    else if (shopList.value.length === 1) form.shopId = String(shopList.value[0]!.id)
  } catch {
    shopList.value = []
  } finally {
    shopLoading.value = false
  }
})

function addSkus(skus: any[]) {
  let goodsId = form.skuList[0]?.erpGoodsId
  let skipped = false
  for (const sku of skus) {
    if (!goodsId) goodsId = String(sku.goodsId)
    if (String(sku.goodsId) !== goodsId) {
      skipped = true
      continue
    }
    if (form.skuList.some(item => item.erpGoodsSkuId === String(sku.id))) continue
    form.skuList.push({
      erpGoodsSkuId: String(sku.id),
      erpGoodsId: String(sku.goodsId),
      skuId: '',
      outerSkuId: sku.skuCode || '',
      skuName: sku.skuName || '默认规格',
      skuCode: sku.skuCode || '',
      img: sku.colorImage || '',
      price: Number(sku.retailPrice) || 0,
      stockNum: 0,
    })
    if (!form.goodsName) form.goodsName = sku.goodsName || ''
    if (!form.goodsNum) form.goodsNum = sku.goodsNum || ''
    if (!form.goodsImg) form.goodsImg = sku.colorImage || ''
  }
  if (skipped) ElMessage.warning('同一店铺商品只能选择同一个商品的规格，其他商品已跳过')
  void formRef.value?.validateField('skuList').catch(() => {})
}

async function submitForm() {
  if (submitting.value || !await formRef.value?.validate().catch(() => false)) return
  if (form.skuList.some(sku => sku.price == null || !Number.isFinite(sku.price) || sku.price < 0 ||
      sku.stockNum == null || !Number.isInteger(sku.stockNum) || sku.stockNum < 0)) {
    ElMessage.warning('请填写每个规格的售价和店铺库存')
    return
  }
  const shop = shopList.value.find(item => String(item.id) === form.shopId)
  if (!shop) {
    ElMessage.warning('请选择有效的店铺')
    return
  }
  submitting.value = true
  try {
    await addShopGoods({
      shopId: form.shopId,
      goodsName: form.goodsName.trim(),
      goodsNum: form.goodsNum.trim(),
      goodsImg: form.goodsImg,
      productId: form.productId.trim(),
      minPrice: Math.min(...form.skuList.map(sku => sku.price!)),
      skuList: form.skuList.map(({ erpGoodsId, ...sku }) => ({
        ...sku,
        skuId: sku.skuId.trim(),
      })),
    })
    ElMessage.success('保存成功')
    await router.replace({
      path: '/goods/shop_goods',
      query: {
        refresh: '1',
        shopId: form.shopId,
        shopType: String(shop.type),
        ...(shop.merchantId != null ? { merchantId: String(shop.merchantId) } : {}),
      },
    })
  } catch {
    // The request interceptor displays the error; retain the form for correction.
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.sku-editor { width: 100%; }
.sku-table { margin-top: 12px; }
.form-tip { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; margin: 8px 0; }
</style>
