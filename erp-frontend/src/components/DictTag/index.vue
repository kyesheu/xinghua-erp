<template>
  <div style="display: inline-block">
    <template v-for="item in options">
      <el-tag v-if="item.value === value" :type="normalizeTagType(item.listClass)" :key="item.value">
        {{ item.label }}
      </el-tag>
    </template>
  </div>
</template>

<script setup lang="ts">
import { normalizeTagType } from '@/utils/tag'
const props = withDefaults(defineProps<{
  options: any[]
  value: string | number | undefined
}>(), {
  options: () => [],
  value: undefined
})

</script>
