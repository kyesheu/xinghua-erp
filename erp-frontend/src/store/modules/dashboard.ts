import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getAiBrief, type AiBriefResponse } from '@/api/dashboard'
import { getToken } from '@/utils/auth'
import { getDashboardOverview, type DashboardOverview } from '@/api/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
  const brief = ref<AiBriefResponse | null>(null)
  const loading = ref(false)
  let scope: string | undefined
  let pending: Promise<void> | undefined
  let version = 0
  const overview = ref<DashboardOverview | null>(null)
  const overviewLoading = ref(false)
  let overviewKey = ''
  let overviewVersion = 0
  let overviewPending: Promise<void> | undefined

  function clearBrief() {
    version++
    scope = undefined
    pending = undefined
    brief.value = null
    loading.value = false
  }
  function clear() {
    clearBrief()
    overviewVersion++
    overview.value = null
    overviewLoading.value = false
    overviewKey = ''
    overviewPending = undefined
  }

  function loadBrief(force = false): Promise<void> {
    // 只保留当前登录、当天的数据，不写入浏览器持久存储。
    const currentScope = JSON.stringify([getToken(), new Date().toDateString()])
    if (scope !== currentScope) {
      clearBrief()
      scope = currentScope
    }
    if (pending) return pending
    if (brief.value && !force) return Promise.resolve()

    loading.value = true
    const requestVersion = version
    pending = (async () => {
      const response: any = await getAiBrief()
      if (!response.data) throw new Error('简报数据为空')
      // 退出登录后，旧请求不得把上一账号的数据重新写入缓存。
      if (version === requestVersion) brief.value = response.data
    })().finally(() => {
      if (version === requestVersion) {
        loading.value = false
        pending = undefined
      }
    })
    return pending
  }

  function loadOverview(days: 7 | 30, force = false): Promise<void> {
    const key = JSON.stringify([getToken(), new Date().toDateString(), days])
    if (key !== overviewKey) {
      overviewKey = key
      overviewVersion++
      overview.value = null
      overviewPending = undefined
    }
    if (overviewPending) return overviewPending
    if (overview.value && !force) return Promise.resolve()
    overviewLoading.value = true
    const requestVersion = overviewVersion
    overviewPending = getDashboardOverview(days).then(response => {
      if (!response.data) throw new Error('经营数据为空')
      if (requestVersion === overviewVersion) overview.value = response.data
    }).finally(() => {
      if (requestVersion === overviewVersion) {
        overviewLoading.value = false
        overviewPending = undefined
      }
    })
    return overviewPending
  }

  return { brief, loading, loadBrief, clear, overview, overviewLoading, loadOverview }
})
