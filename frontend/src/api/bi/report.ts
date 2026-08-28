import request from '@/utils/request'
import type { BiResponse } from '@/api/bi/datasource'

export type ReportStatus = 'ENABLED' | 'DISABLED'

export interface ReportSummary {
  id: string
  reportUuid: string
  reportName: string
  description?: string
  status: ReportStatus
  defaultDatasourceId: string
  defaultProcedureName: string
  componentCount: number
  currentConfigVersion: number
  createdBy: string
  updatedAt: string
  rowVersion: number
  accessUrl: string
}

export interface ReportPage { items: ReportSummary[]; page: number; pageSize: number; total: number }
export interface ReportCreated { reportId: string; reportUuid: string; configVersion: number }
export interface ReportCreate {
  reportName: string
  description?: string
  defaultDatasourceId: string
  defaultProcedureName: string
  maxRows: number
  timeoutSeconds: number
}
export interface ValidationIssue { path: string; code: string; message: string }
export interface ValidationResult { valid: boolean; errors: ValidationIssue[]; warnings: ValidationIssue[] }
export interface ReportField { physicalName: string; displayName: string; dataType: string; displayOrder: number; visible: boolean; fixedPosition: string; alignType: string; fieldRole: string; width?: number; formatPattern?: string; styleIndicatorField?: string }
export interface DrillEdge { targetRouteCode: string; triggerField: string; payloadField: string; routeValue: string; displayOrder: number }
export interface ReportRoute { routeCode: string; routeName: string; viewType: string; fields: ReportField[]; drillEdges: DrillEdge[]; chartConfig?: Record<string, unknown> }
export interface ReportComponent { componentKey: string; componentName: string; regionType: 'COMPONENT' | 'TABLE'; titleVisible?: boolean; displayOrder?: number; layout?: Record<string, unknown>; datasourceIdOverride?: string; procedureNameOverride?: string; signatureHashOverride?: string; routes: ReportRoute[] }
export interface ControlOption { value: string; label: string; displayOrder: number; enabled: boolean }
export interface ReportControl { controlKey: string; label: string; controlType: string; required: boolean; displayOrder: number; optionSource: string; optionDatasourceId?: string; optionSql?: string; options: ControlOption[]; targetComponentKeys: string[]; defaultValue?: Record<string, unknown>; config?: Record<string, unknown> }
export interface ParameterMapping { componentKey?: string; datasourceId: string; procedureName: string; signatureHash: string; parameterOrdinal: number; parameterName: string; mysqlDataType: string; parameterMode: string; sourceType: string; sourceKey?: string; constantValue?: string }
export interface ReportInsight { enabled:boolean; title:string; position:'HEADER'|'FLOAT_RIGHT'|'BOTTOM'; provider:'QWEN'|'DEEPSEEK'; model:string; prompt:string; maxRowsPerComponent:number; maxTokens:number; temperature:number }
export interface ReportConfiguration {
  reportId: string
  reportUuid: string
  expectedVersion: number
  changeSummary?: string
  baseInfo: {
    reportName: string; description?: string; status: ReportStatus; defaultDatasourceId: string
    defaultProcedureName: string; defaultSignatureHash?: string; maxRows: number; timeoutSeconds: number
  }
  acl: { roleIds: string[]; userIds: string[] }
  controls: ReportControl[]
  components: ReportComponent[]
  parameterMappings: ParameterMapping[]
  insight?: ReportInsight
}
export interface VersionSummary { versionNo: number; operationType: string; sourceVersion?: number; changeSummary?: string; createdBy: string; createdAt: string }
export interface VersionPage { items: VersionSummary[]; page: number; pageSize: number; total: number }
export interface VersionDiffItem { path: string; before?: unknown; after?: unknown }
export interface VersionDiff { fromVersion: number; toVersion: number; changes: VersionDiffItem[] }
export interface AclSubjectOption { id: string; label: string; code: string }
export interface InsightProviderStatus { provider:'QWEN'|'DEEPSEEK'; label:string; configured:boolean; source:'DATABASE'|'ENVIRONMENT'|'NONE'; maskedKey:string; credentialVersion:number; updatedAt?:string }

export function listReports(params: Record<string, unknown>): Promise<BiResponse<ReportPage>> {
  return request({ url: '/api/bi/admin/reports', method: 'get', params })
}

export function listAclSubjects(type: 'ROLE' | 'USER', keyword = ''): Promise<BiResponse<AclSubjectOption[]>> {
  return request({ url: '/api/bi/admin/reports/acl-subjects', method: 'get', params: { type, keyword } })
}

export function createReport(data: ReportCreate): Promise<BiResponse<ReportCreated>> {
  return request({ url: '/api/bi/admin/reports', method: 'post', data })
}

export function changeReportStatus(id: string, status: ReportStatus, expectedVersion: number): Promise<BiResponse<void>> {
  return request({ url: `/api/bi/admin/reports/${id}/status`, method: 'put', data: { status, expectedVersion } })
}

export function deleteReport(id: string): Promise<BiResponse<void>> {
  return request({ url: `/api/bi/admin/reports/${id}`, method: 'delete' })
}
export function copyReport(id:string):Promise<BiResponse<ReportCreated>> { return request({url:`/api/bi/admin/reports/${id}/copy`,method:'post'}) }

export function getReportConfiguration(id: string): Promise<BiResponse<ReportConfiguration>> {
  return request({ url: `/api/bi/admin/reports/${id}/configuration`, method: 'get' })
}
export function validateReportConfiguration(id: string, data: ReportConfiguration): Promise<BiResponse<ValidationResult>> {
  return request({ url: `/api/bi/admin/reports/${id}/configuration/validate`, method: 'post', data })
}
export function saveReportConfiguration(id: string, data: ReportConfiguration): Promise<BiResponse<{ configVersion: number }>> {
  return request({ url: `/api/bi/admin/reports/${id}/configuration`, method: 'put', data })
}
export function listReportVersions(id: string): Promise<BiResponse<VersionPage>> {
  return request({ url: `/api/bi/admin/reports/${id}/versions`, method: 'get', params: { page: 1, pageSize: 20 } })
}
export function diffReportVersion(id: string, versionNo: number, against: number): Promise<BiResponse<VersionDiff>> {
  return request({ url: `/api/bi/admin/reports/${id}/versions/${versionNo}/diff`, method: 'get', params: { against } })
}
export function rollbackReportVersion(id: string, versionNo: number, expectedVersion: number, changeSummary: string): Promise<BiResponse<{ configVersion: number }>> {
  return request({ url: `/api/bi/admin/reports/${id}/versions/${versionNo}/rollback`, method: 'post', data: { expectedVersion, changeSummary } })
}
export function listInsightProviders():Promise<BiResponse<InsightProviderStatus[]>> { return request({url:'/api/bi/admin/insight/providers',method:'get'}) }
export function saveInsightProviderKey(provider:'QWEN'|'DEEPSEEK',apiKey:string):Promise<BiResponse<InsightProviderStatus>> { return request({url:`/api/bi/admin/insight/providers/${provider}/credential`,method:'put',data:{apiKey}}) }
export function deleteInsightProviderKey(provider:'QWEN'|'DEEPSEEK'):Promise<BiResponse<void>> { return request({url:`/api/bi/admin/insight/providers/${provider}/credential`,method:'delete'}) }
