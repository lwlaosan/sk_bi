import { createApp } from 'vue'

import 'element-plus/theme-chalk/dark/css-vars.css'
// 通过代码调用的服务组件不会被模板按需解析器识别，需要显式加载样式。
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'

import '@/assets/styles/index.scss' // global css

import App from './App.vue'
import store from './store'
import router from './router'
import directive from './directive' // directive

// 注册指令
import plugins from './plugins' // plugins
import { download } from '@/utils/request'

// svg图标
import 'virtual:svg-icons-register'
import SvgIcon from '@/components/SvgIcon/index.vue'

import './permission' // permission control

import { useDict } from '@/utils/dict'
import { getConfigKey } from "@/api/system/config"
import { parseTime, resetForm, addDateRange, handleTree, selectDictLabel, selectDictLabels } from '@/utils/ruoyi'

// 字典标签组件
import DictTag from '@/components/DictTag/index.vue'

const Pagination = defineAsyncComponent(() => import('@/components/Pagination/index.vue'))
const RightToolbar = defineAsyncComponent(() => import('@/components/RightToolbar/index.vue'))
const Editor = defineAsyncComponent(() => import('@/components/Editor/index.vue'))
const FileUpload = defineAsyncComponent(() => import('@/components/FileUpload/index.vue'))
const ImageUpload = defineAsyncComponent(() => import('@/components/ImageUpload/index.vue'))
const ImagePreview = defineAsyncComponent(() => import('@/components/ImagePreview/index.vue'))

const app = createApp(App)

// 全局方法挂载
app.config.globalProperties.useDict = useDict
app.config.globalProperties.download = download
app.config.globalProperties.parseTime = parseTime
app.config.globalProperties.resetForm = resetForm
app.config.globalProperties.handleTree = handleTree
app.config.globalProperties.addDateRange = addDateRange
app.config.globalProperties.getConfigKey = getConfigKey
app.config.globalProperties.selectDictLabel = selectDictLabel
app.config.globalProperties.selectDictLabels = selectDictLabels

// 全局组件挂载
app.component('DictTag', DictTag)
app.component('Pagination', Pagination)
app.component('FileUpload', FileUpload)
app.component('ImageUpload', ImageUpload)
app.component('ImagePreview', ImagePreview)
app.component('RightToolbar', RightToolbar)
app.component('Editor', Editor)

app.use(router)
app.use(store)
app.use(plugins)
app.component('svg-icon', SvgIcon)

directive(app)

app.mount('#app')
