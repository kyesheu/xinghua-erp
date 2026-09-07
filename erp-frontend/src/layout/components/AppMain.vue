<template>
  <section class="app-main" :class="{ 'with-fixed-header': settingsStore.fixedHeader, 'with-fixed-tags': settingsStore.fixedHeader && settingsStore.tagsView }">
    <router-view>
      <template #default="{ Component, route }">
        <transition name="fade-transform" mode="out-in">
          <keep-alive :include="cachedViews">
            <component :is="Component" :key="route.path" />
          </keep-alive>
        </transition>
      </template>
    </router-view>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useTagsViewStore } from '@/store/modules/tagsView'
import { useSettingsStore } from '@/store/modules/settings'

const tagsViewStore = useTagsViewStore()
const settingsStore = useSettingsStore()
const cachedViews = computed(() => tagsViewStore.cachedViews)
</script>

<style lang="scss" scoped>
.app-main {
  min-height: calc(100vh - 50px);
  width: 100%;
  position: relative;
  overflow: hidden;
  padding: 20px;
}

.app-main.with-fixed-header {
  padding-top: 70px;
}

.app-main.with-fixed-tags {
  padding-top: 104px;
}
</style>
