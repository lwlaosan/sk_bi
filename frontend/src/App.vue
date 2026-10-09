<template>
  <el-config-provider :locale="locale" :size="componentSize">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import Cookies from 'js-cookie'
import locale from 'element-plus/es/locale/lang/zh-cn'
import useSettingsStore from '@/store/modules/settings'
import { handleThemeStyle } from '@/utils/theme'

const configuredSize = Cookies.get('size')
const componentSize = ['large', 'default', 'small'].includes(configuredSize || '') ? configuredSize : 'default'

onMounted(() => {
  nextTick(() => {
    // 初始化主题样式
    handleThemeStyle(useSettingsStore().theme)
  })
})
</script>
