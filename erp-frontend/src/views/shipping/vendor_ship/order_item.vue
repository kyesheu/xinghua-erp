<template>
  <div class="app-container">
    <el-alert title="选择同一订单的商品，按数量分配给供应商或本地仓；分配不会自动通知供应商。" type="info" :closable="false" style="margin-bottom:16px" />
    <OrderItemList />
  </div>
</template>

<script setup lang="ts">
import OrderItemList from '@/views/order/item_list.vue'
</script>
