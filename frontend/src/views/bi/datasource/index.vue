<template>
  <div class="app-container">
    <el-form :model="query" inline>
      <el-form-item label="名称">
        <el-input v-model="query.keyword" clearable placeholder="数据源名称" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
        <el-button icon="Refresh" @click="resetSearch">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row class="mb8">
      <el-button type="primary" plain icon="Plus" v-hasPermi="['bi:datasource:manage']" @click="openCreate">
        新增数据源
      </el-button>
    </el-row>

    <el-table v-loading="loading" :data="rows">
      <el-table-column label="名称" prop="datasourceName" min-width="150" />
      <el-table-column label="主机" min-width="180">
        <template #default="{ row }">{{ connectionLabel(row) }}</template>
      </el-table-column>
      <el-table-column label="数据库" prop="databaseName" min-width="130" />
      <el-table-column label="用户名" prop="username" min-width="120" />
      <el-table-column label="凭据版本" prop="credentialVersion" width="100" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
            {{ row.status === 'ENABLED' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updatedAt" width="190" />
      <el-table-column label="操作" width="245" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" icon="Edit" v-hasPermi="['bi:datasource:manage']" @click="openEdit(row)">修改</el-button>
          <el-button link type="primary" icon="Connection" v-hasPermi="['bi:datasource:manage']" @click="runTest(row)">测试</el-button>
          <el-button link type="primary" icon="List" @click="showProcedures(row)">存储过程</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="query.page" v-model:limit="query.pageSize" @pagination="load" />

    <el-dialog v-model="editorOpen" :title="editingId ? '修改数据源' : '新增数据源'" width="680px" append-to-body>
      <el-alert type="warning" :closable="false" show-icon class="mb16"
        title="密码只在提交时使用，保存后不会返回；留空表示不修改已有密码。" />
      <el-form ref="editorRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="名称" prop="datasourceName"><el-input v-model="form.datasourceName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态" prop="status"><el-radio-group v-model="form.status"><el-radio value="ENABLED">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item></el-col>
          <el-col :span="16"><el-form-item label="主机" prop="host"><el-input v-model="form.host" placeholder="mysql.internal" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="端口" prop="port"><el-input-number v-model="form.port" :min="1" :max="65535" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="数据库" prop="databaseName"><el-input v-model="form.databaseName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="用户名" prop="username"><el-input v-model="form.username" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="密码" :prop="editingId ? undefined : 'password'"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="角色ID"><el-input v-model="roleIdsText" placeholder="逗号分隔" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="用户ID"><el-input v-model="userIdsText" placeholder="逗号分隔" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button @click="editorOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="proceduresOpen" :title="`${procedureSourceName} · 存储过程`" width="560px" append-to-body>
      <el-input v-model="procedureKeyword" clearable placeholder="筛选过程名称" class="mb16" @keyup.enter="loadProcedures" />
      <el-table v-loading="proceduresLoading" :data="procedures" max-height="480">
        <el-table-column label="过程名称" prop="procedureName" />
      </el-table>
      <el-empty v-if="!proceduresLoading && procedures.length === 0" description="未发现存储过程" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="BiDatasource">
import { ElMessage } from 'element-plus'
import {
  createDatasource, getDatasource, listDatasources, listProcedures, testDatasource, updateDatasource,
  type DatasourceSave, type DatasourceView, type ProcedureSummary
} from '@/api/bi/datasource'

const loading = ref(false)
const saving = ref(false)
const rows = ref<DatasourceView[]>([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 20, keyword: '', status: '' })
const editorOpen = ref(false)
const editingId = ref<string>()
const editorRef = ref<{ validate: () => Promise<boolean> }>()
const roleIdsText = ref('')
const userIdsText = ref('')

const blankForm = (): DatasourceSave => ({
  datasourceName: '', host: '', port: 3306, databaseName: '', username: '', password: '',
  connectionProps: { useUnicode: true, characterEncoding: 'utf8' }, status: 'ENABLED', remark: '', roleIds: [], userIds: []
})
const form = reactive<DatasourceSave>(blankForm())
const rules = {
  datasourceName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主机', trigger: 'blur' }],
  databaseName: [{ required: true, message: '请输入数据库名', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const proceduresOpen = ref(false)
const proceduresLoading = ref(false)
const procedureSourceId = ref('')
const procedureSourceName = ref('')
const procedureKeyword = ref('')
const procedures = ref<ProcedureSummary[]>([])

async function load() {
  loading.value = true
  try {
    const response = await listDatasources({ ...query, status: query.status || undefined })
    rows.value = response.data.items
    total.value = response.data.total
  } finally { loading.value = false }
}

function handleSearch() { query.page = 1; load() }
function resetSearch() { query.keyword = ''; query.status = ''; handleSearch() }
function assignForm(value: DatasourceSave) { Object.assign(form, blankForm(), value) }
function parseIds(value: string) { return value.split(',').map(item => item.trim()).filter(item => /^\d+$/.test(item)) }
function connectionLabel(row: DatasourceView) { return row.port > 0 ? `${row.host}:${row.port}` : '已隐藏' }

function openCreate() {
  editingId.value = undefined
  assignForm(blankForm())
  roleIdsText.value = ''; userIdsText.value = ''
  editorOpen.value = true
}

async function openEdit(row: DatasourceView) {
  const response = await getDatasource(row.id)
  editingId.value = row.id
  assignForm({ ...response.data, password: '', expectedRowVersion: response.data.rowVersion })
  roleIdsText.value = response.data.roleIds.join(',')
  userIdsText.value = response.data.userIds.join(',')
  editorOpen.value = true
}

async function save() {
  await editorRef.value?.validate()
  form.roleIds = parseIds(roleIdsText.value)
  form.userIds = parseIds(userIdsText.value)
  saving.value = true
  try {
    if (editingId.value) await updateDatasource(editingId.value, form)
    else await createDatasource(form)
    ElMessage.success('保存成功')
    editorOpen.value = false
    await load()
  } finally { saving.value = false }
}

const testFailText: Record<string, string> = {
  MASTER_KEY_MISSING: '未配置数据源主密钥，无法解密业务库密码',
  DECRYPT_FAILED: '数据源密码无法解密，请在「修改」中重新填写密码后保存再测',
  CREDENTIAL_ERROR: '数据源凭据异常',
  AUTHENTICATION_FAILED: '账号或密码错误',
  TIMEOUT: '连接超时',
  VALIDATION_FAILED: '连接校验失败',
  CONNECTION_FAILED: '无法连接目标数据库'
}

async function runTest(row: DatasourceView) {
  const response = await testDatasource(row.id)
  const result = response.data
  if (result.success) {
    ElMessage.success(`连接成功，耗时 ${result.elapsedMs} ms`)
    return
  }
  const reason = testFailText[result.category] ?? result.category
  const hint = result.warnings?.[0]
  ElMessage.error(hint ? `连接失败：${reason}（${hint}）` : `连接失败：${reason}`)
}

async function showProcedures(row: DatasourceView) {
  procedureSourceId.value = row.id
  procedureSourceName.value = row.datasourceName
  procedureKeyword.value = ''
  proceduresOpen.value = true
  await loadProcedures()
}

async function loadProcedures() {
  proceduresLoading.value = true
  try { procedures.value = (await listProcedures(procedureSourceId.value, procedureKeyword.value)).data }
  finally { proceduresLoading.value = false }
}

load()
</script>
