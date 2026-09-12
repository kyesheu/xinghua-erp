<template>
  <div class="app-container">
    <el-form ref="formRef" :model="goods" :rules="rules" label-width="108px" inline>
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
          <el-col :span="1.5" style="width: 56px">规格1：</el-col>
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
                :value="item.value">
              </el-option>
            </el-select>
          </el-col>
        </el-row>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5" style="width: 60px">规格2：</el-col>
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
                :value="item.value">
              </el-option>
            </el-select>
          </el-col>
        </el-row>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5" style="width: 60px">规格3：</el-col>
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
                :value="item.value">
              </el-option>
            </el-select>
          </el-col>
        </el-row>
      </el-form-item>

      <el-table style="margin-left: 108px;" :data="form.specList" :row-class-name="rowSShopOrderItemIndex" ref="specTableRef">
        <el-table-column label="序号" align="center" prop="index" width="50" />
        <el-table-column label="规格" prop="color" width="150">
          <template #default="scope">
            <el-input v-model="scope.row.colorValue" disabled placeholder="颜色" />
          </template>
        </el-table-column>
        <el-table-column label="规格2" prop="size" width="150">
          <template #default="scope">
            <el-input v-model="scope.row.sizeValue" disabled placeholder="尺码" />
          </template>
        </el-table-column>
        <el-table-column label="规格3" prop="style" width="150">
          <template #default="scope">
            <el-input v-model="scope.row.styleValue" disabled placeholder="款式" />
          </template>
        </el-table-column>
        <el-table-column label="规格编码" prop="specNum" width="150">
          <template #default="scope">
            <el-input v-model="scope.row.specNum" placeholder="规格编码" />
          </template>
        </el-table-column>
        <el-table-column label="预计采购价" prop="purPrice" width="150">
          <template #default="scope">
            <el-input type="number" v-model.number="scope.row.purPrice" placeholder="预计采购价" />
          </template>
        </el-table-column>
      </el-table>
    </el-form>
    <div class="dialog-footer" style="margin-left: 108px; margin-top: 20px; margin-bottom: 50px;">
      <el-button type="primary" @click="submitForm">添加商品SKU</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listCategory } from '@/api/goods/category'
import { addGoodsSku, getGoods } from '@/api/goods/goods'
import { pcaTextArr } from '@/utils/chinaAreaData'
import Treeselect from '@/components/Treeselect/index.vue'
import type { FormInstance } from 'element-plus'

const router = useRouter()
const route = useRoute()


const formRef = ref<FormInstance>()
const specTableRef = ref()

const goods = reactive<Record<string, any>>({})
const form = reactive<Record<string, any>>({
  province: undefined,
  city: undefined,
  town: undefined,
  colorValues: [],
  colorImages: {},
  colorNames: {},
  sizeValues: [],
  styleValues: [],
  outerErpGoodsId: undefined,
  number: '',
  categoryId: undefined,
  specList: [],
  provinces: [],
  shipType: null,
  id: null
})

const dataList = ref<any[]>([])
const colorList = ref<any[]>([])
const sizeList = ref<any[]>([])
const styleList = ref<any[]>([])

const rules = {}



function buildTree(list: any[], parentId: number): any[] {
  const tree: any[] = []
  for (const item of list) {
    if (item.parentId === parentId) {
      tree.push({
        id: item.id,
        parentId: item.parentId,
        label: item.name,
        children: buildTree(list, item.id)
      })
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
  form.specList = []
  if (form.colorValues && form.colorValues.length > 0) {
    if (form.sizeValues && form.sizeValues.length > 0 && form.styleValues && form.styleValues.length > 0) {
      form.colorValues.forEach((c: string) => {
        form.sizeValues.forEach((s: string) => {
          form.styleValues.forEach((st: string) => {
            form.specList.push({
              colorId: 0,
              colorValue: c,
              sizeId: 0,
              sizeValue: s,
              styleId: 0,
              styleValue: st,
              specNum: ''
            })
          })
        })
      })
    } else if (form.sizeValues && form.sizeValues.length > 0) {
      form.colorValues.forEach((c: string) => {
        form.sizeValues.forEach((s: string) => {
          form.specList.push({
            colorId: 0,
            colorValue: c,
            sizeId: 0,
            sizeValue: s,
            styleId: null,
            styleValue: '',
            specNum: ''
          })
        })
      })
    } else if (form.styleValues && form.styleValues.length > 0) {
      form.colorValues.forEach((c: string) => {
        form.styleValues.forEach((st: string) => {
          form.specList.push({
            colorId: 0,
            colorValue: c,
            sizeId: null,
            sizeValue: '',
            styleId: 0,
            styleValue: st,
            specNum: ''
          })
        })
      })
    } else {
      form.colorValues.forEach((x: string) => {
        form.specList.push({
          colorId: 0,
          colorValue: x,
          sizeId: null,
          sizeValue: '',
          styleId: null,
          styleValue: '',
          specNum: ''
        })
      })
    }

    form.colorNames = {}
    form.colorValues.forEach((c: string) => {
      const color = colorList.value.find((x: any) => x.value === c)
      form.colorNames[c] = color || c
    })
  }
}

function rowSShopOrderItemIndex({ row, rowIndex }: { row: any; rowIndex: number }) {
  row.index = rowIndex + 1
}

function submitForm() {
  formRef.value?.validate((valid: boolean) => {
    if (valid) {
      if (!form.specList || form.specList.length === 0) {
        ElMessage.error('请添加商品规格')
        return
      }
      for (const sp of form.specList) {
        if (!sp.specNum) {
          ElMessage.error('商品规格编码不能为空')
          return
        }
      }

      addGoodsSku({ ...form }).then(() => {
        ElMessage.success('商品添加成功')
        router.push('/goods/goods_list')
      })
    }
  })
}

onMounted(() => {
  getCategoryList()
  getGoods(route.query.id as string).then((response: any) => {
    Object.assign(goods, response.data || {})
    form.id = response.data?.id || null
  })
})
</script>
