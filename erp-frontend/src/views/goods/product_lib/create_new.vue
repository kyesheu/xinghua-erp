<template>
  <div class="app-container">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="108px">
      <el-form-item label="商品分类" prop="categoryId">
        <Treeselect :options="dataList" placeholder="请选择上级菜单" v-model="form.categoryId" style="width:220px" @change="categoryChange" />
      </el-form-item>
      <el-form-item label="商品名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入商品名称" />
      </el-form-item>
      <el-form-item label="商品图片" prop="image">
        <image-upload v-model="form.image" category="goods" :limit="1" />
        <el-input v-model="form.image" placeholder="请输入商品图片" />
      </el-form-item>
      <el-form-item label="商品编号" prop="number">
        <el-input v-model="form.number" placeholder="请输入商品编号" style="width:220px" />
      </el-form-item>
      <el-form-item label="预计采购价" prop="purPrice">
        <el-input type="number" v-model.number="form.purPrice" placeholder="请输入预计采购价格" style="width:220px" />
      </el-form-item>
      <el-form-item label="建议零售价" prop="retailPrice">
        <el-input type="number" v-model.number="form.retailPrice" placeholder="请输入建议零售价" style="width:220px" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
      </el-form-item>
      <el-form-item label="发货地" prop="provinces">
        <el-cascader style="width:250px"
          size="large"
          :options="pcaTextArr"
          v-model="form.provinces">
        </el-cascader>
      </el-form-item>
      <el-form-item label="商品规格">
          <div style="width: 100%; margin-bottom: 12px; color: #606266;">每组填写同一类选项，例如规格1填白色、黑色，规格2填3L、5L；不同组会自动组合成SKU。</div>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5" style="width: 70px">规格1：</el-col>
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
          <el-col :span="1.5" style="width: 70px">规格2：</el-col>
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
          <el-col :span="1.5" style="width: 70px">规格3：</el-col>
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
            <el-input v-model.number="scope.row.purPrice" placeholder="预计采购价" />
          </template>
        </el-table-column>
      </el-table>
    </el-form>
    <div class="dialog-footer" style="margin-left: 108px; margin-top: 20px; margin-bottom: 50px;">
      <el-button type="primary" @click="submitForm">添加商品</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listCategory } from '@/api/goods/category'
import { addGoods } from '@/api/goods/goods'
import { pcaTextArr } from '@/utils/chinaAreaData'
import Treeselect from '@/components/Treeselect/index.vue'
import ImageUpload from '@/components/ImageUpload/index.vue'
import type { FormInstance } from 'element-plus'

const router = useRouter()
const route = useRoute()


const formRef = ref<FormInstance>()
const specTableRef = ref()

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
  name: null,
  image: null,
  purPrice: null,
  retailPrice: null,
  remark: null
})

const dataList = ref<any[]>([])
const colorList = ref<any[]>([])
const sizeList = ref<any[]>([])
const styleList = ref<any[]>([])

const rules = {
  categoryId: [{ required: true, message: '请选择分类' }],
  name: [{ required: true, message: '商品名不能为空', trigger: 'blur' }],
  image: [{ required: true, message: '商品图片不能为空', trigger: 'blur' }],
  number: [{ required: true, message: '商品编码不能为空', trigger: 'blur' }],
  purPrice: [{ required: true, message: '请填写预计采购价', trigger: 'blur' }]
}



function categoryChange() {
  // placeholder for category change
}

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
  if (form.colorValues && form.colorValues.length > 0 && !form.number) {
    ElMessage.error('请填写商品编码')
    form.colorValues = []
    form.sizeValues = []
    form.styleValues = []
    return
  }

  form.specList = []

  if (form.colorValues && form.colorValues.length > 0) {
    if (form.sizeValues && form.sizeValues.length > 0 && form.styleValues && form.styleValues.length > 0) {
      form.colorValues.forEach((c: string) => {
        form.sizeValues.forEach((s: string) => {
          form.styleValues.forEach((st: string) => {
            let skuIndex = ''
            if (form.specList.length < 9) {
              skuIndex = '0' + (form.specList.length + 1)
            } else {
              skuIndex = form.specList.length + 1
            }
            const specNum = form.number + skuIndex
            form.specList.push({
              colorId: 0,
              colorValue: c,
              sizeId: 0,
              sizeValue: s,
              styleId: 0,
              styleValue: st,
              purPrice: form.purPrice,
              specNum: specNum
            })
          })
        })
      })
    } else if (form.sizeValues && form.sizeValues.length > 0) {
      form.colorValues.forEach((c: string) => {
        form.sizeValues.forEach((s: string) => {
          let skuIndex = ''
          if (form.specList.length < 9) {
            skuIndex = '0' + (form.specList.length + 1)
          } else {
            skuIndex = form.specList.length + 1
          }
          const specNum = form.number + skuIndex
          form.specList.push({
            colorId: 0,
            colorValue: c,
            sizeId: 0,
            sizeValue: s,
            styleId: null,
            styleValue: '',
            purPrice: form.purPrice,
            specNum: specNum
          })
        })
      })
    } else if (form.styleValues && form.styleValues.length > 0) {
      form.colorValues.forEach((c: string) => {
        form.styleValues.forEach((st: string) => {
          let skuIndex = ''
          if (form.specList.length < 9) {
            skuIndex = '0' + (form.specList.length + 1)
          } else {
            skuIndex = form.specList.length + 1
          }
          const specNum = form.number + skuIndex
          form.specList.push({
            colorId: 0,
            colorValue: c,
            sizeId: null,
            sizeValue: '',
            styleId: 0,
            styleValue: st,
            purPrice: form.purPrice,
            specNum: specNum
          })
        })
      })
    } else {
      form.colorValues.forEach((x: string) => {
        let skuIndex = ''
        if (form.specList.length < 9) {
          skuIndex = '0' + (form.specList.length + 1)
        } else {
          skuIndex = form.specList.length + 1
        }
        const specNum = form.number + skuIndex
        form.specList.push({
          colorId: 0,
          colorValue: x,
          sizeId: null,
          sizeValue: '',
          styleId: null,
          styleValue: '',
          purPrice: form.purPrice,
          specNum: specNum
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

      form.province = form.provinces[0]
      form.city = form.provinces[1]
      form.town = form.provinces[2]

      addGoods({ ...form }).then((response: any) => {
        ElMessage.success('商品添加成功')
        router.push('/goods/goods_list')
      })
    }
  })
}

onMounted(() => {
  getCategoryList()
})
</script>
