<template>
  <div class="app-container">
    <el-tabs v-model="activeName" @tab-click="handleClick">
      <el-tab-pane label="整单分配" name="CompleteShipment">
        <CompleteShipment />
      </el-tab-pane>
      <el-tab-pane label="按商品分配" name="SplitShipment" lazy>
        <SplitShipment />
      </el-tab-pane>
      <el-tab-pane label="供应商任务记录" name="pushList" lazy>
        <PushList />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CompleteShipment from './order_list.vue'
import SplitShipment from './order_item.vue'
import PushList from './assigned_list.vue'

const activeName = ref('CompleteShipment')
function handleClick() { /* noop */ }
</script>