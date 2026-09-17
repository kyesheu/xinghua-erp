<template>
  <div ref="host" class="board-chart" role="img" :aria-label="label" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { AriaComponent, GridComponent, LegendComponent, TooltipComponent, GraphicComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'

echarts.use([BarChart, LineChart, PieChart, AriaComponent, GridComponent, LegendComponent, TooltipComponent, GraphicComponent, CanvasRenderer])
const props = defineProps<{ option: EChartsCoreOption; label: string }>()
const host = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined
let observer: ResizeObserver | undefined
function render() {
  chart?.setOption({ ...props.option, aria: { enabled: true, label: { description: props.label } } }, { notMerge: true })
  // Empty-series updates must not retain ECharts' previous generated data description.
  host.value?.setAttribute('aria-label', props.label)
}
onMounted(() => {
  if (!host.value) return
  chart = echarts.init(host.value)
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(host.value)
})
watch(() => props.option, render, { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>

<style scoped>
.board-chart { width: 100%; height: 100%; min-height: 190px; }
</style>
