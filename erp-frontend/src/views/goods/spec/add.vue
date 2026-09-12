<template>
  <div class="app-container">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="108px" inline>
      <el-col :span="24">
        <el-form-item label="商品图片" prop="image">
          <el-image :src="goods.image" disabled style="width: 200px" />
        </el-form-item>
      </el-col>
      <el-col :span="24">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="goods.name" disabled placeholder="请输入商品名称" style="width:800px" />
        </el-form-item>
      </el-col>
      <el-col :span="24">
        <el-form-item label="商品编号" prop="goodsNum">
          <el-input v-model="goods.goodsNum" disabled placeholder="请输入商品编号" style="width:220px" />
        </el-form-item>
      </el-col>

      <el-form-item label="商品规格">
          <div style="width: 100%; margin-bottom: 12px; color: #606266;">每组填写同一类选项，例如规格1填白色、黑色，规格2填3L、5L；不同组会自动组合成SKU。</div>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5" style="width: 68px">规格1：</el-col>
          <el-col :span="20">
            <el-select
              v-model="form.colorValues"
              multiple
              filterable
              allow-create
              default-first-option
              @change="onSpecChange"
              placeholder="输入同一类选项，回车添加（如白色、黑色）">
              <el-option
                v-for="item in colorList"
                :key="item.value"
                :label="item.label"
                :value="item.value" />
            </el-select>
          </el-col>
        </el-row>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5" style="width: 68px">规格2：</el-col>
          <el-col :span="20">
            <el-select
              v-model="form.sizeValues"
              multiple
              filterable
              allow-create
              default-first-option
              @change="onSpecChange"
              placeholder="输入另一类选项，回车添加（如3L、5L）">
              <el-option
                v-for="item in sizeList"
                :key="item.value"
                :label="item.label"
                :value="item.value" />
            </el-select>
          </el-col>
        </el-row>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5" style="width: 68px">规格3：</el-col>
          <el-col :span="20">
            <el-select
              v-model="form.styleValues"
              multiple
              filterable
              allow-create
              default-first-option
              @change="onSpecChange"
              placeholder="输入第三类选项，回车添加（选填）">
              <el-option
                v-for="item in styleList"
                :key="item.value"
                :label="item.label"
                :value="item.value" />
            </el-select>
          </el-col>
        </el-row>
      </el-form-item>

      <el-table style="margin-left: 108px;" :data="form.specList" :row-class-name="rowSShopOrderItemIndex" ref="sShopOrderItemRef">
        <el-table-column label="序号" align="center" prop="index" width="50" />
        <el-table-column label="规格" prop="color" width="120">
          <template #default="scope">
            <el-input v-model="scope.row.colorValue" disabled placeholder="颜色" />
          </template>
        </el-table-column>
        <el-table-column label="规格2" prop="size" width="120">
          <template #default="scope">
            <el-input v-model="scope.row.sizeValue" disabled placeholder="尺码" />
          </template>
        </el-table-column>
        <el-table-column label="规格3" prop="style" width="120">
          <template #default="scope">
            <el-input v-model="scope.row.styleValue" disabled placeholder="款式" />
          </template>
        </el-table-column>
        <el-table-column label="规格编码" prop="specNum" width="250">
          <template #default="scope">
            <el-input v-model="scope.row.specNum" placeholder="规格编码" />
          </template>
        </el-table-column>
        <el-table-column label="条形码" prop="barCode" width="250">
          <template #default="scope">
            <el-input v-model="scope.row.barCode" placeholder="条形码" />
          </template>
        </el-table-column>
        <el-table-column label="外部SkuId" prop="outerErpSkuId" width="220">
          <template #default="scope">
            <el-input v-model="scope.row.outerErpSkuId" placeholder="外部ID" />
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="100">
          <template #default="scope">
            <el-input v-model="scope.row.unit" placeholder="单位" />
          </template>
        </el-table-column>
        <el-table-column label="采购价" prop="purPrice" width="120" v-if="form.priceType==0">
          <template #default="scope">
            <el-input v-model="scope.row.purPrice" placeholder="采购价" @input="handlePurPriceInput(scope.row)" />
          </template>
        </el-table-column>
        <el-table-column label="零售价" prop="retailPrice" width="120" v-if="form.priceType==0">
          <template #default="scope">
            <el-input v-model="scope.row.retailPrice" placeholder="零售价" @input="handleRetailPriceInput(scope.row)" />
          </template>
        </el-table-column>
      </el-table>
    </el-form>
    <div style="margin-left: 108px;margin-top:20px;margin-bottom: 50px;">
      <el-button type="primary" @click="submitForm">添加商品SKU</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listCategory } from '@/api/goods/category'
import { listCategoryAttributeValue } from '@/api/goods/categoryAttributeValue'
import { addGoodsSku, getGoods } from '@/api/goods/goods'
import { listSupplier } from '@/api/goods/supplier'
import { listCategoryAttribute } from '@/api/goods/categoryAttribute'
import { limitDecimalLength, stringToNumber } from '@/utils/numberInput'
import { useTagsViewStore } from '@/store/modules/tagsView'

const route = useRoute()
const router = useRouter()
const tagsViewStore = useTagsViewStore()

const formRef = ref()
const sShopOrderItemRef = ref()

const supplierList = ref<any[]>([])

const goods = reactive<Record<string, any>>({
  image: null,
  name: null,
  goodsNum: null,
  shipType: null
})

const form = reactive<Record<string, any>>({
  province: undefined,
  city: undefined,
  town: undefined,
  colorValues: undefined,
  colorImages: {},
  colorNames: {},
  sizeValues: undefined,
  styleValues: undefined,
  outerErpGoodsId: undefined,
  number: '',
  categoryId: undefined,
  specList: [],
  provinces: [],
  shipType: null,
  id: null,
  priceType: null
})

const categoryAttributeList = ref<any[]>([])
const colorList = ref<any[]>([])
const sizeList = ref<any[]>([])
const styleList = ref<any[]>([])
const dataList = ref<any[]>([{ id: 'fruits', label: 'Fruits', children: [] }])

const rules = {}

function categoryChange(node: any, instanceId: number) {
  console.log('====分类边哈11111====', node, instanceId)
  console.log('====分类边哈====', form.categoryId)
  if (node) {
    form.categoryId = node.id
    console.log('====分类边哈2====', form.categoryId)
    let topCategoryId = 0
    if (node.parentId === 0) topCategoryId = node.id
    else topCategoryId = node.parentId
    console.log('====分类边哈22222====', topCategoryId)
    colorList.value = []
    sizeList.value = []
    styleList.value = []
    listCategoryAttribute({ categoryId: topCategoryId }).then((response: any) => {
      categoryAttributeList.value = response.rows || []
      if (response.rows) {
        response.rows.forEach((x: any) => {
          listCategoryAttributeValue({ categoryAttributeId: x.id }).then((resp: any) => {
            if (x.code === 'color') {
              colorList.value = resp.rows || []
            } else if (x.code === 'size') {
              sizeList.value = resp.rows || []
            } else if (x.code === 'style') {
              styleList.value = resp.rows || []
            }
          })
        })
      }
    })
  }
}

function getRowDate(row: any) {}



function buildTree(list: any[], parentId: number): any[] {
  const tree: any[] = []
  for (let i = 0; i < list.length; i++) {
    if (list[i].parentId === parentId) {
      const node = {
        id: list[i].id,
        parentId: list[i].parentId,
        label: list[i].name,
        children: buildTree(list, list[i].id)
      }
      tree.push(node)
    }
  }
  return tree
}

function getCategoryList() {
  listCategory({}).then((response: any) => {
    dataList.value = buildTree(response.rows || [], 0)
  })
}

function onSpecChange() {
  if (form.colorValues && form.colorValues.length > 0) {
    form.specList = []
    if (form.sizeValues && form.sizeValues.length > 0 && form.styleValues && form.styleValues.length > 0) {
      form.colorValues.forEach((c: string) => {
        form.sizeValues.forEach((s: string) => {
          form.styleValues.forEach((st: string) => {
            const spec = {
              colorId: 0,
              colorValue: c,
              sizeId: 0,
              sizeValue: s,
              styleId: 0,
              styleValue: st,
              specNum: goods.goodsNum + '-',
              outerErpSkuId: '',
              barCode: '',
              unit: '',
              weight1: null,
              weight2: null,
              weight3: null,
              purPrice: null,
              retailPrice: null
            }
            form.specList.push(spec)
          })
        })
      })
    } else if (form.sizeValues && form.sizeValues.length > 0) {
      form.colorValues.forEach((c: string) => {
        form.sizeValues.forEach((s: string) => {
          const spec = {
            colorId: 0,
            colorValue: c,
            sizeId: 0,
            sizeValue: s,
            styleId: null,
            styleValue: '',
            specNum: goods.goodsNum + '-',
            outerErpSkuId: '',
            barCode: '',
            unit: '',
            weight1: null,
            weight2: null,
            weight3: null,
            purPrice: null,
            retailPrice: null
          }
          form.specList.push(spec)
        })
      })
    } else if (form.styleValues && form.styleValues.length > 0) {
      // 选择了款式 && 颜色
    } else {
      form.colorValues.forEach((x: string) => {
        const spec = {
          colorId: 0,
          colorValue: x,
          sizeId: null,
          sizeValue: '',
          styleId: null,
          styleValue: '',
          specNum: goods.goodsNum + '-',
          outerErpSkuId: '',
          barCode: '',
          unit: '',
          weight1: null,
          weight2: null,
          weight3: null,
          purPrice: null,
          retailPrice: null
        }
        form.specList.push(spec)
      })
    }

    form.colorNames = {}
    form.colorValues.forEach((c: string) => {
      const color = colorList.value.find((x: any) => x.id === c)
      form.colorNames[c] = color
    })
  }
}

function rowSShopOrderItemIndex({ row, rowIndex }: { row: any; rowIndex: number }) {
  row.index = rowIndex + 1
  return ''
}

function submitForm() {
  console.log('=====添加商品===', form)
  formRef.value?.validate((valid: boolean) => {
    if (valid) {
      if (!form.specList || form.specList.length === 0) {
        ElMessage.error('请添加商品规格')
        return
      }
      for (let i = 0; i < form.specList.length; i++) {
        const sp = form.specList[i]
        if (!sp.specNum) {
          ElMessage.error('商品规格编码不能为空')
          return
        }
        if (form.priceType == 0) {
          if (sp.purPrice == null) {
            ElMessage.error('请填写采购价')
            return
          }
          if (sp.retailPrice == null) {
            ElMessage.error('请填写零售价')
            return
          }
          sp.purPrice = stringToNumber(sp.purPrice)
          sp.retailPrice = stringToNumber(sp.retailPrice)
        }
      }
      addGoodsSku(form).then((response: any) => {
        if (response.code == 200) {
          ElMessage.success('商品SKU添加成功')
          tagsViewStore.delView(route)
          router.push('/goods/goods_list')
        } else {
          ElMessage.error(response.msg)
        }
      })
    }
  })
}

function handlePurPriceInput(row: any) {
  row.purPrice = limitDecimalLength(row.purPrice)
}

function handleRetailPriceInput(row: any) {
  row.retailPrice = limitDecimalLength(row.retailPrice)
}

onMounted(() => {
  getCategoryList()
  listSupplier({ pageNum: 1, pageSize: 100 }).then((resp: any) => {
    supplierList.value = resp.rows || []
  })
  getGoods(route.query.id as string).then((response: any) => {
    Object.assign(goods, response.data || {})
    goods.shipType = response.data.shipType + ''
    form.id = response.data.id
    form.priceType = response.data.priceType
  })
})
</script>
