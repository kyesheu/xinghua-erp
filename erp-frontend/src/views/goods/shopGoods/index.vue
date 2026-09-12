<template>
  <div class="app-container">
    <el-tabs v-model="activeName" @tab-click="handleClick">
      <el-tab-pane v-for="item in typeList" :key="item.id" :label="item.name" :name="String(item.id)" lazy>
        <ShopGoods :shop-type="item.id" :refresh="refresh" :target-shop-id="targetShopId" :platform-list="platformList" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import ShopGoods from '@/views/shop/goods/index.vue'
import { listPlatform } from '@/api/shop/shop'

const route = useRoute()

const activeName = ref('')
const typeList = ref<any[]>([])
const platformList = ref<any[]>([])
const refresh = ref('')
const targetShopId = ref('')
const targetShopType = ref('')

watch(() => route.query, (query) => {
  if (query.refresh !== '1') return
  const type = String(query.shopType || '')
  if (typeList.value.some(item => String(item.id) === type)) activeName.value = type
  refresh.value = '1'
  targetShopId.value = String(query.shopId || '')
})

onMounted(() => {
  refresh.value = (route.query.refresh as string) || ''
  targetShopId.value = (route.query.shopId as string) || ''
  targetShopType.value = (route.query.shopType as string) || ''

  listPlatform({ status: 0 }).then((res: any) => {
    platformList.value = res.rows || []
    const platforms = res.rows || []
    typeList.value = [
      ...platforms.filter((item: any) => String(item.id) === '999'),
      ...platforms.filter((item: any) => String(item.id) !== '999'),
    ]
    if (targetShopType.value) {
      const targetPlatform = typeList.value.find((p: any) => p.id == targetShopType.value)
      if (targetPlatform) {
        activeName.value = String(targetPlatform.id)
      }
    } else if (typeList.value.length > 0) {
      activeName.value = String(typeList.value[0].id)
    }
  })
})

function handleClick(tab: any) {
  console.log(tab)
}
</script>
