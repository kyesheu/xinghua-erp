<template>
  <div
    v-show="visible"
    ref="bar"
    class="table-viewport-scrollbar"
    :style="barStyle"
    role="region"
    aria-label="表格横向滚动条"
    tabindex="0"
    @scroll="syncTable"
  >
    <div :style="{ width: contentWidth + 'px', height: '1px' }" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const bar = ref<HTMLElement>()
const visible = ref(false)
const barStyle = ref<Record<string, string>>({})
const contentWidth = ref(0)
let activeWrap: HTMLElement | null = null
let frame = 0
let mutationObserver: MutationObserver | undefined
let resizeObserver: ResizeObserver | undefined

function scheduleUpdate() {
  if (!frame) frame = requestAnimationFrame(update)
}

function update() {
  frame = 0
  const main = document.querySelector('.app-main')
  if (!main) return
  const mainRect = main.getBoundingClientRect()
  const candidates = Array.from(main.querySelectorAll<HTMLElement>('.el-table'))
    .map((table) => {
      const wrap = table.querySelector<HTMLElement>(':scope > .el-table__inner-wrapper > .el-table__body-wrapper > .el-scrollbar > .el-scrollbar__wrap')
      const rect = table.getBoundingClientRect()
      return { table, wrap, rect }
    })
    .filter(({ wrap, rect }) => wrap && wrap.scrollWidth > wrap.clientWidth + 1 && rect.width > 0 && rect.height > 0 && rect.top < window.innerHeight && rect.bottom > 0)

  // 当同一页面有多个表格时，优先操作视口内可见高度最大的表格。
  candidates.sort((a, b) =>
    Math.min(b.rect.bottom, window.innerHeight) - Math.max(b.rect.top, 0) -
    (Math.min(a.rect.bottom, window.innerHeight) - Math.max(a.rect.top, 0)),
  )
  const selected = candidates[0]
  if (!selected?.wrap) {
    activeWrap = null
    visible.value = false
    return
  }
  const wrapRect = selected.wrap.getBoundingClientRect()
  const left = Math.max(wrapRect.left, mainRect.left, 0)
  const right = Math.min(wrapRect.right, mainRect.right, window.innerWidth)
  if (right <= left) {
    visible.value = false
    activeWrap = null
    return
  }
  activeWrap = selected.wrap
  contentWidth.value = right - left + selected.wrap.scrollWidth - selected.wrap.clientWidth
  barStyle.value = {
    left: `${left}px`,
    width: `${right - left}px`,
    top: `${Math.min(selected.rect.bottom, window.innerHeight) - 14}px`,
  }
  visible.value = true
  nextTick(() => {
    if (bar.value && activeWrap) bar.value.scrollLeft = activeWrap.scrollLeft
  })
}

function syncTable() {
  if (bar.value && activeWrap) activeWrap.scrollLeft = bar.value.scrollLeft
}

onMounted(() => {
  const main = document.querySelector('.app-main')
  if (!main) return
  resizeObserver = new ResizeObserver(scheduleUpdate)
  const observeTables = () => {
    resizeObserver?.disconnect()
    resizeObserver?.observe(main)
    main.querySelectorAll('.el-table, .el-table__body-wrapper .el-scrollbar__wrap, .el-table__body-wrapper .el-scrollbar__view').forEach((element) => resizeObserver?.observe(element))
    scheduleUpdate()
  }
  mutationObserver = new MutationObserver(observeTables)
  mutationObserver.observe(main, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style'] })
  window.addEventListener('resize', scheduleUpdate)
  document.addEventListener('scroll', scheduleUpdate, true)
  observeTables()
})

onBeforeUnmount(() => {
  mutationObserver?.disconnect()
  resizeObserver?.disconnect()
  window.removeEventListener('resize', scheduleUpdate)
  document.removeEventListener('scroll', scheduleUpdate, true)
  cancelAnimationFrame(frame)
})
</script>

<style scoped>
.table-viewport-scrollbar {
  position: fixed;
  z-index: 8;
  height: 14px;
  overflow-x: auto;
  overflow-y: hidden;
  background: var(--el-fill-color-light);
  scrollbar-color: var(--el-text-color-secondary) var(--el-fill-color-light);
  scrollbar-width: thin;
}
.table-viewport-scrollbar::-webkit-scrollbar {
  height: 14px;
}
.table-viewport-scrollbar::-webkit-scrollbar-thumb {
  background: var(--el-text-color-secondary);
  border: 3px solid var(--el-fill-color-light);
  border-radius: 7px;
}
</style>
