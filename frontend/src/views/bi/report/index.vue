<template>
  <div class="app-container">
    <el-form :model="query" inline>
      <el-form-item label="名称">
        <el-input v-model="query.keyword" clearable placeholder="报表名称" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="search">查询</el-button>
        <el-button icon="Refresh" @click="resetSearch">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row class="mb8">
      <el-button type="primary" plain icon="Plus" v-hasPermi="['bi:report:create']" @click="openCreate">
        新建报表
      </el-button>
    </el-row>

    <el-table v-loading="loading" :data="rows">
      <el-table-column label="名称" prop="reportName" min-width="170" show-overflow-tooltip />
      <el-table-column label="UUID" min-width="265">
        <template #default="{ row }">
          <span class="uuid">{{ row.reportUuid }}</span>
          <el-button link type="primary" icon="CopyDocument" @click="copyUuid(row.reportUuid)" />
        </template>
      </el-table-column>
      <el-table-column label="默认过程" prop="defaultProcedureName" min-width="170" show-overflow-tooltip />
      <el-table-column label="组件" prop="componentCount" width="75" align="center" />
      <el-table-column label="版本" prop="currentConfigVersion" width="75" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.status" active-value="ENABLED" inactive-value="DISABLED"
            v-hasPermi="['bi:report:design']" @change="toggleStatus(row, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updatedAt" width="190" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" icon="Edit" v-hasPermi="['bi:report:design']" @click="openDesigner(row)">设计</el-button>
          <el-button link type="primary" icon="CopyDocument" v-hasPermi="['bi:report:create']" @click="copy(row)">复制</el-button>
          <el-button v-if="row.status==='ENABLED'" link type="success" icon="View" v-hasPermi="['bi:report:view']" @click="openRuntime(row)">运行</el-button>
          <el-button link type="danger" icon="Delete" v-hasPermi="['bi:report:design']" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="query.page" v-model:limit="query.pageSize" @pagination="load" />

    <el-dialog v-model="createOpen" title="新建报表" width="620px" append-to-body>
      <el-alert type="info" :closable="false" show-icon class="mb16"
        title="创建后自动生成下方主表格和 ROOT 根层级，报表默认处于停用状态。" />
      <el-form ref="createRef" :model="form" :rules="rules" label-width="115px">
        <el-form-item label="报表名称" prop="reportName"><el-input v-model="form.reportName" maxlength="150" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="默认数据源" prop="defaultDatasourceId">
          <el-select v-model="form.defaultDatasourceId" filterable style="width: 100%" @change="sourceChanged">
            <el-option v-for="item in datasourceOptions" :key="item.id" :label="item.datasourceName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认存储过程" prop="defaultProcedureName">
          <el-select v-model="form.defaultProcedureName" filterable style="width: 100%" :loading="proceduresLoading">
            <el-option v-for="item in procedureOptions" :key="item.procedureName" :label="item.procedureName" :value="item.procedureName" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="最大行数" prop="maxRows"><el-input-number v-model="form.maxRows" :min="1" :max="200000" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="超时秒数" prop="timeoutSeconds"><el-input-number v-model="form.timeoutSeconds" :min="1" :max="600" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
        <el-button @click="createOpen = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="BiReport">
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDatasources, listProcedures, type DatasourceView, type ProcedureSummary } from '@/api/bi/datasource'
import { changeReportStatus, copyReport, createReport, deleteReport, listReports,
  type ReportCreate, type ReportStatus, type ReportSummary } from '@/api/bi/report'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const rows = ref<ReportSummary[]>([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 20, keyword: '', status: '' })
const createOpen = ref(false)
const createRef = ref<{ validate: () => Promise<boolean> }>()
const datasourceOptions = ref<DatasourceView[]>([])
const procedureOptions = ref<ProcedureSummary[]>([])
const proceduresLoading = ref(false)

const blankForm = (): ReportCreate => ({ reportName: '', description: '', defaultDatasourceId: '',
  defaultProcedureName: '', maxRows: 50000, timeoutSeconds: 60 })
const form = reactive<ReportCreate>(blankForm())
const rules = {
  reportName: [{ required: true, message: '请输入报表名称', trigger: 'blur' }],
  defaultDatasourceId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
  defaultProcedureName: [{ required: true, message: '请选择存储过程', trigger: 'change' }]
}

async function load() {
  loading.value = true
  try {
    const response = await listReports({ ...query, status: query.status || undefined })
    rows.value = response.data.items
    total.value = response.data.total
  } finally { loading.value = false }
}

function search() { query.page = 1; load() }
function resetSearch() { query.keyword = ''; query.status = ''; search() }

async function openCreate() {
  Object.assign(form, blankForm())
  procedureOptions.value = []
  createOpen.value = true
  const response = await listDatasources({ page: 1, pageSize: 100, status: 'ENABLED' })
  datasourceOptions.value = response.data.items
}

async function sourceChanged(id: string) {
  form.defaultProcedureName = ''
  procedureOptions.value = []
  if (!id) return
  proceduresLoading.value = true
  try { procedureOptions.value = (await listProcedures(id)).data }
  finally { proceduresLoading.value = false }
}

async function submitCreate() {
  await createRef.value?.validate()
  saving.value = true
  try {
    await createReport(form)
    ElMessage.success('报表创建成功')
    createOpen.value = false
    await load()
  } finally { saving.value = false }
}

async function toggleStatus(row: ReportSummary, value: unknown) {
  const status = value as ReportStatus
  if (status === row.status) return
  try {
    await changeReportStatus(row.id, status, row.currentConfigVersion)
    ElMessage.success(status === 'ENABLED' ? '报表已启用' : '报表已停用')
    await load()
  } catch { /* 请求拦截器已展示错误，保留原状态 */ }
}

async function remove(row: ReportSummary) {
  await ElMessageBox.confirm(`确认删除报表“${row.reportName}”吗？`, '删除确认', { type: 'warning' })
  await deleteReport(row.id)
  ElMessage.success('删除成功')
  await load()
}
async function copy(row:ReportSummary){await copyReport(row.id);ElMessage.success('报表副本已创建');await load()}

async function copyUuid(uuid: string) {
  await navigator.clipboard.writeText(uuid)
  ElMessage.success('UUID 已复制')
}
function openDesigner(row: ReportSummary) { router.push(`/bi/report-design/index/${row.id}`) }
function openRuntime(row: ReportSummary) { router.push(`/bi/report-run/index/${row.reportUuid}`) }

load()
</script>

<style scoped>
.uuid { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
</style>
