<template>
  <div v-loading="loading" class="designer">
    <div class="designer-header">
      <el-button link icon="ArrowLeft" @click="router.push('/bi/report')">返回报表列表</el-button>
      <div class="title">{{ config?.baseInfo.reportName || '报表设计' }} <el-tag v-if="config" size="small">v{{ config.expectedVersion }}</el-tag></div>
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
    </div>
    <el-tabs v-if="config" v-model="tab" tab-position="left" class="designer-body">
      <el-tab-pane label="基础信息" name="base">
        <el-form :model="config.baseInfo" label-width="130px" class="form-panel">
          <el-form-item label="报表名称"><el-input v-model="config.baseInfo.reportName" maxlength="150" /></el-form-item>
          <el-form-item label="描述"><el-input v-model="config.baseInfo.description" type="textarea" maxlength="1000" /></el-form-item>
          <el-form-item label="状态"><el-radio-group v-model="config.baseInfo.status"><el-radio value="DISABLED">停用</el-radio><el-radio value="ENABLED">启用</el-radio></el-radio-group></el-form-item>
          <el-form-item label="默认数据源"><el-select v-model="config.baseInfo.defaultDatasourceId" filterable style="width:100%" @change="sourceChanged"><el-option v-for="item in sources" :key="item.id" :label="item.datasourceName" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="默认存储过程"><el-select v-model="config.baseInfo.defaultProcedureName" filterable style="width:100%"><el-option v-for="item in procedures" :key="item.procedureName" :label="item.procedureName" :value="item.procedureName" /></el-select></el-form-item>
          <el-row :gutter="16"><el-col :span="12"><el-form-item label="最大行数"><el-input-number v-model="config.baseInfo.maxRows" :min="1" :max="200000" /></el-form-item></el-col><el-col :span="12"><el-form-item label="超时秒数"><el-input-number v-model="config.baseInfo.timeoutSeconds" :min="1" :max="600" /></el-form-item></el-col></el-row>
          <el-form-item label="变更摘要"><el-input v-model="config.changeSummary" maxlength="500" placeholder="说明本次修改" /></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="权限" name="acl">
        <el-form label-width="110px" class="form-panel">
          <el-alert type="info" :closable="false" class="mb16" title="系统管理员始终拥有访问权限；其他用户满足任一用户或角色授权即可访问。" />
          <el-form-item label="授权角色"><el-select v-model="config.acl.roleIds" multiple filterable remote reserve-keyword collapse-tags collapse-tags-tooltip :remote-method="searchRoles" :loading="roleLoading" placeholder="输入角色名称或编码搜索" style="width:100%"><el-option v-for="item in aclRoleOptions" :key="item.id" :label="`${item.label}（${item.code}）`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="授权用户"><el-select v-model="config.acl.userIds" multiple filterable remote reserve-keyword collapse-tags collapse-tags-tooltip :remote-method="searchUsers" :loading="userLoading" placeholder="输入昵称或账号搜索" style="width:100%"><el-option v-for="item in aclUserOptions" :key="item.id" :label="`${item.label}（${item.code}）`" :value="item.id" /></el-select></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="查询控件" name="controls">
        <div class="structure-tools"><el-button type="primary" plain icon="Plus" @click="addControl">新增控件</el-button></div>
        <el-alert class="mb16" type="info" :closable="false" title="SQL 选项只适用单选/多选；必须返回 value、label 两列，且不要写末尾分号。" />
        <el-table :data="config.controls" border :row-key="controlRowKey" :expand-row-keys="expandedControls" @expand-change="onControlExpand">
          <el-table-column type="expand">
            <template #default="scope">
              <div class="expand-panel">
                <el-form label-width="100px">
                  <el-form-item label="目标组件"><el-select v-model="scope.row.targetComponentKeys" multiple style="width:420px"><el-option v-for="item in config.components" :key="item.componentKey" :label="item.componentName" :value="item.componentKey" /></el-select></el-form-item>
                  <template v-if="scope.row.optionSource === 'SQL'">
                    <el-form-item label="选项数据源"><el-select v-model="scope.row.optionDatasourceId" filterable placeholder="选择执行选项 SQL 的数据源" style="width:320px"><el-option v-for="item in sources" :key="item.id" :label="item.datasourceName" :value="item.id" /></el-select></el-form-item>
                    <el-form-item label="选项 SQL">
                      <el-input v-model="scope.row.optionSql" type="textarea" :rows="5" placeholder="SELECT sale_man AS value, sale_man AS label FROM bn_dw.dim_customer" />
                      <div class="sql-hint">必须恰好两列且别名为 value、label；禁止末尾分号、注释和多语句；可用 :currentUserId 过滤当前用户。</div>
                    </el-form-item>
                  </template>
                </el-form>
                <template v-if="scope.row.optionSource === 'STATIC'">
                  <div class="section-title">静态选项 <el-button link type="primary" icon="Plus" @click="addOption(scope.row)">添加选项</el-button></div>
                  <el-table :data="scope.row.options" size="small"><el-table-column label="值"><template #default="{ row }"><el-input v-model="row.value" /></template></el-table-column><el-table-column label="标签"><template #default="{ row }"><el-input v-model="row.label" /></template></el-table-column><el-table-column label="启用" width="70"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column><el-table-column width="60"><template #default="optionScope"><el-button link type="danger" icon="Delete" @click="scope.row.options.splice(optionScope.$index, 1)" /></template></el-table-column></el-table>
                </template>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="控件键" min-width="130"><template #default="{ row }"><el-input v-model="row.controlKey" /></template></el-table-column>
          <el-table-column label="标签" min-width="130"><template #default="{ row }"><el-input v-model="row.label" /></template></el-table-column>
          <el-table-column label="类型" width="170"><template #default="{ row }"><el-select v-model="row.controlType"><el-option v-for="item in controlTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></template></el-table-column>
          <el-table-column label="选项来源" width="130"><template #default="{ row }"><el-select v-model="row.optionSource" @change="onOptionSourceChange(row)"><el-option label="无" value="NONE"/><el-option label="静态" value="STATIC"/><el-option label="SQL" value="SQL"/></el-select></template></el-table-column>
          <el-table-column label="必填" width="70"><template #default="{ row }"><el-switch v-model="row.required" /></template></el-table-column>
          <el-table-column label="操作" width="65"><template #default="scope"><el-button link type="danger" icon="Delete" @click="config.controls.splice(scope.$index, 1)" /></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="参数映射" name="mappings">
        <el-alert type="info" :closable="false" class="mb16" title="从当前默认存储过程同步参数后配置来源；p_user_id 始终由服务端当前用户注入。" />
        <div class="structure-tools"><el-button type="primary" plain icon="Refresh" :loading="syncing" @click="syncParameters">同步默认过程参数</el-button></div>
        <el-table :data="defaultMappings" border>
          <el-table-column prop="parameterOrdinal" label="#" width="55" />
          <el-table-column prop="parameterName" label="参数" min-width="150" />
          <el-table-column prop="mysqlDataType" label="参数类型" width="130" />
          <el-table-column label="来源" width="180"><template #default="{ row }"><el-select v-model="row.sourceType" :disabled="isReservedParameter(row.parameterName)"><el-option v-for="type in sourceTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select></template></el-table-column>
          <el-table-column label="来源键" min-width="190"><template #default="{ row }"><el-input v-model="row.sourceKey" :disabled="isReservedParameter(row.parameterName) || ['NULL','CONSTANT'].includes(row.sourceType)" placeholder="如 ctrl_date.start" /></template></el-table-column>
          <el-table-column label="常量" min-width="150"><template #default="{ row }"><el-input v-model="row.constantValue" :disabled="row.sourceType !== 'CONSTANT'" /></template></el-table-column>
        </el-table>
        <template v-if="overrideComponents.length">
          <div class="section-title">组件覆盖过程</div>
          <div v-for="component in overrideComponents" :key="component.componentKey" class="mapping-group">
            <strong>{{ component.componentName }}（{{ component.componentKey }}）</strong>
            <el-button link type="primary" icon="Refresh" @click="syncComponentParameters(component)">同步参数</el-button>
            <el-table :data="componentMappings(component.componentKey)" size="small" border>
              <el-table-column prop="parameterOrdinal" label="#" width="55"/><el-table-column prop="parameterName" label="参数" min-width="150"/><el-table-column prop="mysqlDataType" label="类型" width="110"/>
              <el-table-column label="来源" width="180"><template #default="{row}"><el-select v-model="row.sourceType" :disabled="isReservedParameter(row.parameterName)"><el-option v-for="type in sourceTypes" :key="type.value" :label="type.label" :value="type.value"/></el-select></template></el-table-column>
              <el-table-column label="来源键"><template #default="{row}"><el-input v-model="row.sourceKey" :disabled="isReservedParameter(row.parameterName)||['NULL','CONSTANT'].includes(row.sourceType)"/></template></el-table-column>
              <el-table-column label="常量"><template #default="{row}"><el-input v-model="row.constantValue" :disabled="row.sourceType!=='CONSTANT'"/></template></el-table-column>
            </el-table>
          </div>
        </template>
      </el-tab-pane>
      <el-tab-pane label="组件与字段映射" name="structure">
        <el-alert class="mb16" type="info" :closable="false" title="在这里维护组件尺寸和排列，并将存储过程结果列映射为页面字段；物理字段必须与结果集别名完全一致。" />
        <div class="structure-tools"><el-button type="primary" plain icon="Plus" @click="addComponent">新增上方组件</el-button></div>
        <el-collapse v-model="expandedComponents">
          <el-collapse-item v-for="(component, componentIndex) in config.components" :key="component.componentKey" :name="component.componentKey">
            <template #title><strong>{{ component.componentName }}</strong><el-tag class="region-tag" size="small">{{ component.regionType }}</el-tag><span class="muted">{{ component.componentKey }}</span></template>
            <el-form inline label-width="95px">
              <el-form-item label="名称"><el-input v-model="component.componentName" /></el-form-item>
              <el-form-item label="组件键"><el-input v-model="component.componentKey" :disabled="component.regionType === 'TABLE'" /></el-form-item>
              <el-form-item v-if="component.regionType === 'COMPONENT'" label="视图"><el-select v-model="component.routes[0].viewType" style="width:170px"><el-option v-for="item in viewOptions" :key="item.value" :label="item.label" :value="item.value"><span>{{ item.label }}</span><span class="option-code">{{ item.value }}</span></el-option></el-select></el-form-item>
              <el-form-item v-if="component.regionType === 'COMPONENT'" label="宽度(12格)"><el-input-number v-model="component.layout!.w" :min="1" :max="12" /></el-form-item>
              <el-form-item v-if="component.regionType === 'COMPONENT'" label="高度(格)"><el-input-number v-model="component.layout!.h" :min="5" :max="20" /></el-form-item>
              <el-form-item v-if="component.regionType === 'COMPONENT'" label="排列"><el-button-group><el-button :disabled="componentIndex===0" @click="moveComponent(componentIndex,-1)">上移</el-button><el-button :disabled="componentIndex===config.components.length-1" @click="moveComponent(componentIndex,1)">下移</el-button></el-button-group></el-form-item>
              <el-form-item v-if="component.regionType === 'COMPONENT'"><el-button type="danger" link @click="removeComponent(componentIndex)">删除组件</el-button></el-form-item>
            </el-form>
            <el-form inline label-width="90px" class="override-row">
              <el-form-item label="覆盖数据源"><el-select v-model="component.datasourceIdOverride" clearable style="width:190px" @change="componentSourceChanged(component)"><el-option v-for="item in sources" :key="item.id" :label="item.datasourceName" :value="item.id"/></el-select></el-form-item>
              <el-form-item v-if="component.datasourceIdOverride" label="覆盖过程"><el-select v-model="component.procedureNameOverride" filterable style="width:210px"><el-option v-for="item in overrideProcedures[component.componentKey]||[]" :key="item.procedureName" :label="item.procedureName" :value="item.procedureName"/></el-select></el-form-item>
              <el-form-item v-if="component.procedureNameOverride"><el-button type="primary" plain @click="syncComponentParameters(component)">同步覆盖参数</el-button></el-form-item>
            </el-form>
            <div class="section-title">路由层级 <el-button v-if="component.regionType === 'TABLE'" link type="primary" icon="Plus" @click="addRoute(component)">新增路由</el-button></div>
            <el-tabs type="border-card">
              <el-tab-pane v-for="(routeItem, routeIndex) in component.routes" :key="routeItem.routeCode" :label="routeItem.routeCode">
                <el-form inline><el-form-item label="路由编码"><el-input v-model="routeItem.routeCode" :disabled="routeItem.routeCode === 'ROOT'" /></el-form-item><el-form-item label="名称"><el-input v-model="routeItem.routeName" /></el-form-item><el-form-item v-if="routeItem.routeCode !== 'ROOT'"><el-button link type="danger" @click="removeRoute(component, routeIndex)">删除路由</el-button></el-form-item></el-form>
                <div class="section-title">结果字段映射 <span class="section-tip">物理字段 = 存储过程 SELECT 别名</span> <el-tooltip placement="top" :content="roleHelp"><el-tag class="role-help" size="small" type="info">角色说明 ?</el-tag></el-tooltip> <el-button link type="primary" icon="Plus" @click="addField(routeItem)">新增字段</el-button></div>
                <el-table :data="routeItem.fields" size="small" border>
                  <el-table-column label="物理字段" min-width="150"><template #default="{ row }"><el-input v-model="row.physicalName" /></template></el-table-column>
                  <el-table-column label="显示名" min-width="140"><template #default="{ row }"><el-input v-model="row.displayName" /></template></el-table-column>
                  <el-table-column label="类型" width="125"><template #default="{ row }"><el-select v-model="row.dataType"><el-option v-for="type in fieldTypes" :key="type" :label="type" :value="type"/></el-select></template></el-table-column>
                  <el-table-column label="角色" width="175"><template #default="{ row }"><el-select v-model="row.fieldRole"><el-option v-for="role in roleOptions" :key="role.value" :label="role.label" :value="role.value"/></el-select></template></el-table-column>
                  <el-table-column label="显示" width="70" align="center"><template #default="{ row }"><el-switch v-model="row.visible" /></template></el-table-column>
                  <el-table-column label="固定" width="105"><template #default="{ row }"><el-select v-model="row.fixedPosition"><el-option label="不固定" value="NONE"/><el-option label="左侧" value="LEFT"/><el-option label="右侧" value="RIGHT"/></el-select></template></el-table-column>
                  <el-table-column label="最小宽度" width="125"><template #default="{ row }"><el-input-number v-model="row.width" :min="60" :max="600" controls-position="right" /></template></el-table-column>
                  <el-table-column label="对齐" width="105"><template #default="{ row }"><el-select v-model="row.alignType"><el-option label="左" value="LEFT"/><el-option label="中" value="CENTER"/><el-option label="右" value="RIGHT"/></el-select></template></el-table-column>
                  <el-table-column label="格式" min-width="130"><template #default="{ row }"><el-input v-model="row.formatPattern" placeholder="如 #,##0.00" /></template></el-table-column>
                  <el-table-column label="样式字段" min-width="150"><template #default="{ row }"><el-input v-model="row.styleIndicatorField" placeholder="如 F_amount_style" /></template></el-table-column>
                  <el-table-column label="操作" width="65"><template #default="scope"><el-button link type="danger" icon="Delete" @click="routeItem.fields.splice(scope.$index, 1)" /></template></el-table-column>
                </el-table>
                <template v-if="component.regionType === 'TABLE'">
                  <div class="section-title">钻取边 <el-button link type="primary" icon="Plus" @click="addEdge(routeItem)">新增钻取</el-button></div>
                  <el-table :data="routeItem.drillEdges" size="small" border>
                    <el-table-column label="目标路由" width="135"><template #default="{ row }"><el-select v-model="row.targetRouteCode"><el-option v-for="target in component.routes" :key="target.routeCode" :label="target.routeCode" :value="target.routeCode"/></el-select></template></el-table-column>
                    <el-table-column label="触发字段"><template #default="{ row }"><el-select v-model="row.triggerField"><el-option v-for="field in routeItem.fields" :key="field.physicalName" :label="field.physicalName" :value="field.physicalName"/></el-select></template></el-table-column>
                    <el-table-column label="载荷字段"><template #default="{ row }"><el-select v-model="row.payloadField"><el-option v-for="field in routeItem.fields" :key="field.physicalName" :label="field.physicalName" :value="field.physicalName"/></el-select></template></el-table-column>
                    <el-table-column label="路由值"><template #default="{ row }"><el-input v-model="row.routeValue" /></template></el-table-column>
                    <el-table-column label="操作" width="65"><template #default="scope"><el-button link type="danger" icon="Delete" @click="routeItem.drillEdges.splice(scope.$index, 1)" /></template></el-table-column>
                  </el-table>
                </template>
              </el-tab-pane>
            </el-tabs>
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>
      <el-tab-pane label="洞察配置" name="insight">
        <el-form v-if="config.insight" :model="config.insight" label-width="140px" class="form-panel insight-form">
          <el-alert type="info" :closable="false" class="mb16" title="API Key 由服务端加密保存，不进入报表版本，也不会回传完整内容。洞察只使用使用者当前页面已查询的数据。" />
          <div class="provider-credentials">
            <div class="section-title">模型 API Key</div>
            <div v-for="provider in insightProviders" :key="provider.provider" class="provider-row">
              <div class="provider-name"><strong>{{ provider.label }}</strong><el-tag :type="provider.configured?'success':'info'" size="small">{{ provider.configured?'已配置':'未配置' }}</el-tag><span v-if="provider.maskedKey" class="muted">{{ provider.maskedKey }} · {{ provider.source==='DATABASE'?'页面配置':'环境变量' }}</span></div>
              <el-input v-model="providerSecrets[provider.provider]" type="password" show-password maxlength="500" autocomplete="new-password" :placeholder="provider.configured?'输入新 Key 可替换现有配置':'请输入 API Key'" />
              <el-button v-hasPermi="['bi:datasource:manage']" type="primary" plain :loading="credentialSaving===provider.provider" :disabled="!providerSecrets[provider.provider]" @click="saveProviderCredential(provider.provider)">保存 Key</el-button>
              <el-button v-if="provider.source==='DATABASE'" v-hasPermi="['bi:datasource:manage']" type="danger" link @click="removeProviderCredential(provider.provider)">清除</el-button>
            </div>
            <div class="sql-hint">完整密钥只在保存时提交一次，服务端 AES-256-GCM 加密保存；页面仅显示末四位。环境变量配置仍可作为兜底。</div>
          </div>
          <el-form-item label="启用洞察"><el-switch v-model="config.insight.enabled" /></el-form-item>
          <template v-if="config.insight.enabled">
            <el-form-item label="入口标题"><el-input v-model="config.insight.title" maxlength="50" /></el-form-item>
            <el-form-item label="入口位置"><el-radio-group v-model="config.insight.position"><el-radio value="HEADER">报表头部</el-radio><el-radio value="FLOAT_RIGHT">右侧悬浮</el-radio><el-radio value="BOTTOM">报表底部</el-radio></el-radio-group></el-form-item>
            <el-form-item label="模型供应商"><el-select v-model="config.insight.provider" style="width:100%" @change="insightProviderChanged"><el-option label="阿里云千问" value="QWEN"/><el-option label="DeepSeek" value="DEEPSEEK"/></el-select></el-form-item>
            <el-form-item label="模型名称"><el-select v-model="config.insight.model" filterable allow-create default-first-option style="width:100%"><el-option v-for="model in insightModels[config.insight.provider]" :key="model" :label="model" :value="model"/></el-select></el-form-item>
            <el-form-item label="开发者提示词"><el-input v-model="config.insight.prompt" type="textarea" :rows="10" maxlength="8000" show-word-limit placeholder="例如：分析销售趋势、异常日期和毛利变化，并给出三条可执行建议。" /></el-form-item>
            <el-row :gutter="16"><el-col :span="8"><el-form-item label="每组件最多行"><el-input-number v-model="config.insight.maxRowsPerComponent" :min="1" :max="200" /></el-form-item></el-col><el-col :span="8"><el-form-item label="最大输出 Token"><el-input-number v-model="config.insight.maxTokens" :min="128" :max="8192" /></el-form-item></el-col><el-col :span="8"><el-form-item label="温度"><el-input-number v-model="config.insight.temperature" :min="0" :max="2" :step="0.1" /></el-form-item></el-col></el-row>
          </template>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="版本" name="versions">
        <el-table :data="versions"><el-table-column prop="versionNo" label="版本" width="80" /><el-table-column prop="operationType" label="操作" width="100" /><el-table-column prop="changeSummary" label="摘要" /><el-table-column prop="createdBy" label="操作人" width="100" /><el-table-column prop="createdAt" label="时间" width="190" /><el-table-column label="操作" width="140"><template #default="{row}"><el-button link type="primary" :disabled="row.versionNo===config.expectedVersion" @click="showDiff(row)">差异</el-button><el-button link type="warning" :disabled="row.versionNo===config.expectedVersion" @click="rollback(row)">回滚</el-button></template></el-table-column></el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts" name="BiReportDesign">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onBeforeRouteLeave } from 'vue-router'
import { deleteInsightProviderKey, diffReportVersion, getReportConfiguration, listAclSubjects, listInsightProviders, listReportVersions, rollbackReportVersion, saveInsightProviderKey, saveReportConfiguration, validateReportConfiguration, type AclSubjectOption, type InsightProviderStatus, type ParameterMapping, type ReportConfiguration, type ReportComponent, type ReportControl, type ReportRoute, type VersionSummary } from '@/api/bi/report'
import { getProcedureParameters, listDatasources, listProcedures, type DatasourceView, type ProcedureSummary } from '@/api/bi/datasource'

const route = useRoute(); const router = useRouter(); const reportId = String(route.params.reportId)
const loading = ref(true); const saving = ref(false); const tab = ref('base')
const syncing = ref(false)
const config = ref<ReportConfiguration>(); const sources = ref<DatasourceView[]>([]); const procedures = ref<ProcedureSummary[]>([]); const versions = ref<VersionSummary[]>([])
const aclRoleOptions = ref<AclSubjectOption[]>([]); const aclUserOptions = ref<AclSubjectOption[]>([])
const roleLoading = ref(false); const userLoading = ref(false)
const expandedComponents = ref<string[]>([])
const savedSnapshot = ref('')
const overrideProcedures = reactive<Record<string, ProcedureSummary[]>>({})
const fieldTypes = ['STRING', 'NUMBER', 'DATE', 'DATETIME', 'BOOLEAN', 'JSON']
const roleOptions = [
  { value: 'DIMENSION', label: '维度 DIMENSION' },
  { value: 'MEASURE', label: '度量 MEASURE' },
  { value: 'VALUE', label: '值 VALUE' },
  { value: 'PAYLOAD', label: '下钻载荷 PAYLOAD' },
  { value: 'STYLE', label: '样式 STYLE' }
]
const roleHelp = '维度：分类、分组或图表坐标轴；度量：可聚合的数值系列；值：指标卡或普通结果值；下钻载荷：传递到下一层的隐藏数据；样式：供其他字段引用的颜色、加粗等隐藏指令。'
const insightModels:Record<'QWEN'|'DEEPSEEK',string[]>={QWEN:['qwen-plus','qwen-max','qwen-turbo'],DEEPSEEK:['deepseek-v4-flash','deepseek-v4-pro']}
const insightProviders=ref<InsightProviderStatus[]>([]),providerSecrets=reactive<Record<'QWEN'|'DEEPSEEK',string>>({QWEN:'',DEEPSEEK:''}),credentialSaving=ref('')
const viewOptions = [
  { value: 'KPI', label: '指标卡' },
  { value: 'BAR', label: '柱状图' },
  { value: 'STACKED_BAR', label: '堆叠柱状图' },
  { value: 'HORIZONTAL_BAR', label: '横向条形图' },
  { value: 'LINE', label: '折线图' },
  { value: 'AREA', label: '面积图' },
  { value: 'PIE', label: '饼图' },
  { value: 'DONUT', label: '环形图' },
  { value: 'GAUGE', label: '仪表盘' }
]
const controlTypeOptions = [
  { value: 'TEXT', label: '文本' },
  { value: 'SINGLE_SELECT', label: '单选' },
  { value: 'MULTI_SELECT', label: '多选' },
  { value: 'DATE', label: '日期' },
  { value: 'DATE_RANGE', label: '日期范围' },
  { value: 'NUMBER', label: '数字' },
  { value: 'NUMBER_RANGE', label: '数字范围' }
]
const sourceTypes = [
  { value: 'SYSTEM', label: '系统参数（SYSTEM）' },
  { value: 'REGION', label: '报表区域（REGION）' },
  { value: 'COMPONENT', label: '报表组件（COMPONENT）' },
  { value: 'CONTROL', label: '查询控件（CONTROL）' },
  { value: 'DRILL', label: '下钻参数（DRILL）' },
  { value: 'CONSTANT', label: '固定常量（CONSTANT）' },
  { value: 'NULL', label: '空值（NULL）' }
]
const expandedControls = ref<string[]>([])
// 控件行用稳定 ID 做 row-key，避免编辑 controlKey 时表格重挂载导致输入框失焦
const controlUidMap = new WeakMap<ReportControl, string>()
let controlUidSeq = 0
function controlRowKey(row: ReportControl) {
  let id = controlUidMap.get(row)
  if (!id) {
    id = `ctrl_row_${++controlUidSeq}`
    controlUidMap.set(row, id)
  }
  return id
}
const defaultMappings = computed(() => config.value?.parameterMappings.filter((item: ParameterMapping) => !item.componentKey) || [])
const overrideComponents = computed(() => config.value?.components.filter((item: ReportComponent) => !!item.procedureNameOverride) || [])
function mergeOptions(current: AclSubjectOption[], incoming: AclSubjectOption[]) {
  return Array.from(new Map([...current, ...incoming].map(item => [item.id, item])).values())
}
async function searchRoles(keyword = '') { roleLoading.value = true; try { aclRoleOptions.value = mergeOptions(aclRoleOptions.value, (await listAclSubjects('ROLE', keyword)).data) } finally { roleLoading.value = false } }
async function searchUsers(keyword = '') { userLoading.value = true; try { aclUserOptions.value = mergeOptions(aclUserOptions.value, (await listAclSubjects('USER', keyword)).data) } finally { userLoading.value = false } }

async function load() {
  loading.value = true
  try {
    const [configuration, datasourcePage] = await Promise.all([getReportConfiguration(reportId), listDatasources({ page: 1, pageSize: 100, status: 'ENABLED' }), searchRoles(), searchUsers()])
    config.value = configuration.data; sources.value = datasourcePage.data.items
    config.value.insight ||= {enabled:false,title:'智能洞察',position:'HEADER',provider:'QWEN',model:'qwen-plus',prompt:'请分析当前报表的关键趋势、异常变化和可能原因，并给出三条可执行建议。所有结论必须有页面数据依据。',maxRowsPerComponent:50,maxTokens:2048,temperature:0.2}
    config.value.components.forEach((item: ReportComponent) => { item.layout ||= {}; item.layout.w ||= item.regionType === 'COMPONENT' ? 6 : 12; item.layout.h ||= 8 })
    expandedComponents.value = config.value.components.map((item: ReportComponent) => item.componentKey)
    expandedControls.value = config.value.controls.map((item: ReportControl) => controlRowKey(item))
    await Promise.all([sourceChanged(config.value.baseInfo.defaultDatasourceId, false), loadVersions(),loadInsightProviders()])
    await Promise.all(config.value.components.filter((c: ReportComponent) => !!c.datasourceIdOverride).map((c: ReportComponent) => componentSourceChanged(c, false)))
    savedSnapshot.value = JSON.stringify(config.value)
  } finally { loading.value = false }
}
function insightProviderChanged(provider:'QWEN'|'DEEPSEEK'){if(config.value?.insight)config.value.insight.model=insightModels[provider][0]}
async function loadInsightProviders(){try{insightProviders.value=(await listInsightProviders()).data}catch{insightProviders.value=[]}}
async function saveProviderCredential(provider:'QWEN'|'DEEPSEEK'){const key=providerSecrets[provider]?.trim();if(!key)return;credentialSaving.value=provider;try{await saveInsightProviderKey(provider,key);providerSecrets[provider]='';await loadInsightProviders();ElMessage.success('模型 API Key 已加密保存')}finally{credentialSaving.value=''}}
async function removeProviderCredential(provider:'QWEN'|'DEEPSEEK'){await ElMessageBox.confirm('确认清除页面保存的模型 API Key？如果配置了环境变量，将自动恢复使用环境变量。','清除模型密钥',{type:'warning'});await deleteInsightProviderKey(provider);await loadInsightProviders();ElMessage.success('页面保存的模型 API Key 已清除')}
async function sourceChanged(id: string, clear = true) { if (!config.value) return; if (clear) config.value.baseInfo.defaultProcedureName = ''; procedures.value = (await listProcedures(id)).data }
async function loadVersions() { try { versions.value = (await listReportVersions(reportId)).data.items } catch { versions.value = [] } }
async function save() {
  if (!config.value) return
  saving.value = true
  try {
    const validation = (await validateReportConfiguration(reportId, config.value)).data
    if (!validation.valid) { ElMessage.error(validation.errors[0]?.message || '配置校验失败'); return }
    const result = await saveReportConfiguration(reportId, config.value)
    config.value.expectedVersion = result.data.configVersion; config.value.changeSummary = ''
    savedSnapshot.value = JSON.stringify(config.value)
    ElMessage.success('配置已保存并生成新版本'); await loadVersions()
  } finally { saving.value = false }
}
function addComponent() {
  if (!config.value) return
  const index = config.value.components.filter((item: ReportComponent) => item.regionType === 'COMPONENT').length + 1
  const component: ReportComponent = { componentKey: `component_${index}`, componentName: `组件 ${index}`, regionType: 'COMPONENT', titleVisible: true, layout: { x: 0, y: 0, w: 6, h: 8 }, routes: [{ routeCode: 'ROOT', routeName: '根层级', viewType: 'BAR', fields: [], drillEdges: [] }] }
  config.value.components.push(component); expandedComponents.value.push(component.componentKey)
}
function removeComponent(index: number) { config.value?.components.splice(index, 1) }
function moveComponent(index:number,delta:number){if(!config.value)return;const target=index+delta;if(target<0||target>=config.value.components.length)return;const [item]=config.value.components.splice(index,1);config.value.components.splice(target,0,item);config.value.components.forEach((component:ReportComponent,order:number)=>component.displayOrder=order)}
function addRoute(component: ReportComponent) { const index = component.routes.length; component.routes.push({ routeCode: `LEVEL_${index}`, routeName: `层级 ${index}`, viewType: 'TABLE', fields: [], drillEdges: [] }) }
function removeRoute(component: ReportComponent, index: number) { component.routes.splice(index, 1) }
function addField(routeItem: ReportRoute) { routeItem.fields.push({ physicalName: '', displayName: '', dataType: 'STRING', displayOrder: routeItem.fields.length, visible: true, fixedPosition: 'NONE', width: 120, alignType: 'LEFT', fieldRole: 'VALUE' }) }
function addEdge(routeItem: ReportRoute) { routeItem.drillEdges.push({ targetRouteCode: '', triggerField: '', payloadField: '', routeValue: '', displayOrder: routeItem.drillEdges.length }) }
function addControl() {
  if (!config.value) return
  const index = config.value.controls.length + 1
  const control: ReportControl = { controlKey: `control_${index}`, label: `查询条件 ${index}`, controlType: 'TEXT', required: false, displayOrder: config.value.controls.length, optionSource: 'NONE', options: [], targetComponentKeys: config.value.components.map((item: ReportComponent) => item.componentKey) }
  config.value.controls.push(control)
  expandedControls.value = [...expandedControls.value, controlRowKey(control)]
}
function expandControl(row: ReportControl) {
  const key = controlRowKey(row)
  if (!expandedControls.value.includes(key)) expandedControls.value = [...expandedControls.value, key]
}
function onControlExpand(_row: ReportControl, expandedRows: ReportControl[]) {
  expandedControls.value = expandedRows.map((item: ReportControl) => controlRowKey(item))
}
function onOptionSourceChange(row: ReportControl) {
  expandControl(row)
  if (row.optionSource === 'SQL' && !row.optionDatasourceId && config.value?.baseInfo.defaultDatasourceId) {
    row.optionDatasourceId = config.value.baseInfo.defaultDatasourceId
  }
}
function addOption(control: ReportControl) { control.options.push({ value: '', label: '', displayOrder: control.options.length, enabled: true }) }
function isUserParameter(name: string) { return name.toLowerCase() === 'p_user_id' }
function isReservedParameter(name: string) { return !!reservedSource(name) }
function reservedSource(name: string) { const key=name.toLowerCase(); if(key==='p_user_id')return {sourceType:'SYSTEM',sourceKey:'user_id'};if(key==='p_region_key')return {sourceType:'REGION',sourceKey:'region_key'};if(key==='p_component_key')return {sourceType:'COMPONENT',sourceKey:'component_key'};if(key==='p_drill_field')return {sourceType:'DRILL',sourceKey:'field'};if(key==='p_drill_value')return {sourceType:'DRILL',sourceKey:'value'};return undefined }
async function syncParameters() {
  if (!config.value || !config.value.baseInfo.defaultDatasourceId || !config.value.baseInfo.defaultProcedureName) return
  syncing.value = true
  try {
    const metadata = (await getProcedureParameters(config.value.baseInfo.defaultDatasourceId, config.value.baseInfo.defaultProcedureName)).data
    const existing = new Map<number, ParameterMapping>(defaultMappings.value.map((item: ParameterMapping) => [item.parameterOrdinal, item]))
    const synced: ParameterMapping[] = metadata.parameters.map(parameter => {
      const old = existing.get(parameter.ordinal)
      const reserved = reservedSource(parameter.name)
      return { componentKey: undefined, datasourceId: config.value!.baseInfo.defaultDatasourceId, procedureName: metadata.procedureName, signatureHash: metadata.signatureHash, parameterOrdinal: parameter.ordinal, parameterName: parameter.name, mysqlDataType: parameter.mysqlDataType, parameterMode: parameter.mode,
        sourceType: reserved?.sourceType || old?.sourceType || 'NULL', sourceKey: reserved?.sourceKey || old?.sourceKey, constantValue: old?.constantValue }
    })
    config.value.parameterMappings = [...config.value.parameterMappings.filter((item: ParameterMapping) => !!item.componentKey), ...synced]
    config.value.baseInfo.defaultSignatureHash = metadata.signatureHash
    ElMessage.success(`已同步 ${synced.length} 个参数`)
  } finally { syncing.value = false }
}
function componentMappings(key: string) { return config.value?.parameterMappings.filter((item: ParameterMapping) => item.componentKey === key) || [] }
async function componentSourceChanged(component: ReportComponent, clear = true) {
  if (clear) { component.procedureNameOverride = undefined; component.signatureHashOverride = undefined; if (config.value) config.value.parameterMappings = config.value.parameterMappings.filter((m: ParameterMapping) => m.componentKey !== component.componentKey) }
  overrideProcedures[component.componentKey] = component.datasourceIdOverride ? (await listProcedures(component.datasourceIdOverride)).data : []
}
async function syncComponentParameters(component: ReportComponent) {
  if (!config.value || !component.datasourceIdOverride || !component.procedureNameOverride) return
  const metadata = (await getProcedureParameters(component.datasourceIdOverride, component.procedureNameOverride)).data
  const old = new Map<number, ParameterMapping>(componentMappings(component.componentKey).map((m: ParameterMapping) => [m.parameterOrdinal, m]))
  const synced: ParameterMapping[] = metadata.parameters.map(parameter => { const previous=old.get(parameter.ordinal), reserved=reservedSource(parameter.name); return { componentKey:component.componentKey,datasourceId:component.datasourceIdOverride!,procedureName:metadata.procedureName,signatureHash:metadata.signatureHash,parameterOrdinal:parameter.ordinal,parameterName:parameter.name,mysqlDataType:parameter.mysqlDataType,parameterMode:parameter.mode,sourceType:reserved?.sourceType||previous?.sourceType||'NULL',sourceKey:reserved?.sourceKey||previous?.sourceKey,constantValue:previous?.constantValue } })
  config.value.parameterMappings=[...config.value.parameterMappings.filter((m: ParameterMapping)=>m.componentKey!==component.componentKey),...synced];component.signatureHashOverride=metadata.signatureHash;ElMessage.success(`已同步 ${component.componentName} 的 ${synced.length} 个参数`)
}
async function showDiff(version: VersionSummary) { if (!config.value) return; const diff=(await diffReportVersion(reportId,config.value.expectedVersion,version.versionNo)).data; const text=diff.changes.slice(0,30).map(item=>`${item.path}: ${JSON.stringify(item.before)} → ${JSON.stringify(item.after)}`).join('\n'); await ElMessageBox.alert(text||'两个版本无差异',`v${version.versionNo} 与当前版本差异`,{customClass:'version-diff'}) }
async function rollback(version: VersionSummary) { if (!config.value) return; await ElMessageBox.confirm(`确认将当前配置回滚到 v${version.versionNo}？回滚会生成新版本。`,'版本回滚',{type:'warning'}); await rollbackReportVersion(reportId,version.versionNo,config.value.expectedVersion,`回滚到 v${version.versionNo}`);ElMessage.success('回滚成功');await load() }
const dirty = computed(() => !!config.value && !!savedSnapshot.value && JSON.stringify(config.value) !== savedSnapshot.value)
onBeforeRouteLeave(async () => { if (!dirty.value) return true; try { await ElMessageBox.confirm('当前配置尚未保存，确认离开吗？','未保存提示',{type:'warning'}); return true } catch { return false } })
function beforeUnload(event:BeforeUnloadEvent){if(dirty.value){event.preventDefault();event.returnValue=''}}
window.addEventListener('beforeunload',beforeUnload);onBeforeUnmount(()=>window.removeEventListener('beforeunload',beforeUnload))
load()
</script>

<style scoped>
.designer{min-height:calc(100vh - 84px);background:#fff}.designer-header{height:58px;display:flex;align-items:center;gap:20px;padding:0 20px;border-bottom:1px solid #e5e7eb}.designer-header .title{flex:1;font-size:17px;font-weight:600}.designer-body{box-sizing:border-box;height:calc(100vh - 142px);padding:20px;overflow:hidden}.designer-body :deep(.el-tabs__content){overflow-y:auto;overflow-x:hidden}.form-panel{max-width:780px;padding:10px 24px}.structure-tools{margin-bottom:12px}.region-tag{margin-left:12px}.muted{margin-left:12px;color:#909399;font-weight:400}.section-title{margin:14px 0 8px;font-weight:600}.section-tip{margin-left:8px;color:#909399;font-size:12px;font-weight:400}.role-help{margin-left:8px;cursor:help}.option-code{float:right;margin-left:24px;color:#909399;font-size:12px}.expand-panel{padding:12px 24px;background:#f8fafc}.sql-hint{margin-top:6px;color:#909399;font-size:12px;line-height:1.5}.mapping-group{margin:12px 0 20px}.override-row{padding:8px;background:#f8fafc}.provider-credentials{margin-bottom:22px;padding:4px 16px 16px;background:#f8fafc;border:1px solid #ebeef5;border-radius:6px}.provider-row{display:grid;grid-template-columns:minmax(210px,1fr) minmax(240px,1.2fr) auto auto;gap:10px;align-items:center;margin:10px 0}.provider-name{display:flex;align-items:center;gap:8px}@media(max-width:900px){.provider-row{grid-template-columns:1fr}.provider-name{flex-wrap:wrap}}
</style>
