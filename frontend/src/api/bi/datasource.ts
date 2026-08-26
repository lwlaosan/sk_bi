import request from '@/utils/request'

export interface BiResponse<T> {
  code: 'OK'
  message: string
  data: T
  traceId: string
}

export interface DatasourceView {
  id: string
  datasourceName: string
  host: string
  port: number
  databaseName: string
  username: string
  hasPassword: boolean
  connectionProps: Record<string, unknown>
  credentialVersion: number
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  roleIds: string[]
  userIds: string[]
  createdAt: string
  updatedAt: string
  rowVersion: number
}

export interface DatasourcePage {
  items: DatasourceView[]
  page: number
  pageSize: number
  total: number
}

export interface DatasourceSave {
  datasourceName: string
  host: string
  port: number
  databaseName: string
  username: string
  password?: string
  connectionProps: Record<string, unknown>
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  roleIds: string[]
  userIds: string[]
  expectedRowVersion?: number
}

export interface ProcedureSummary { procedureName: string }
export interface ProcedureParameter { ordinal: number; name: string; mode: string; mysqlDataType: string; dtdIdentifier: string }
export interface ProcedureMetadata { procedureName: string; signatureHash: string; supported: boolean; parameters: ProcedureParameter[]; unsupportedReasons: string[] }
export interface ConnectionTest { success: boolean; elapsedMs: number; category: string; warnings: string[] }

export function listDatasources(params: Record<string, unknown>): Promise<BiResponse<DatasourcePage>> {
  return request({ url: '/api/bi/admin/datasources', method: 'get', params })
}

export function getDatasource(id: string): Promise<BiResponse<DatasourceView>> {
  return request({ url: `/api/bi/admin/datasources/${id}`, method: 'get' })
}

export function createDatasource(data: DatasourceSave): Promise<BiResponse<DatasourceView>> {
  return request({ url: '/api/bi/admin/datasources', method: 'post', data })
}

export function updateDatasource(id: string, data: DatasourceSave): Promise<BiResponse<DatasourceView>> {
  return request({ url: `/api/bi/admin/datasources/${id}`, method: 'put', data })
}

export function testDatasource(id: string): Promise<BiResponse<ConnectionTest>> {
  return request({ url: `/api/bi/admin/datasources/${id}/test`, method: 'post' })
}

export function listProcedures(id: string, keyword = ''): Promise<BiResponse<ProcedureSummary[]>> {
  return request({ url: `/api/bi/admin/datasources/${id}/procedures`, method: 'get', params: { keyword } })
}

export function getProcedureParameters(id: string, procedureName: string): Promise<BiResponse<ProcedureMetadata>> {
  return request({ url: `/api/bi/admin/datasources/${id}/procedures/${encodeURIComponent(procedureName)}/parameters`, method: 'get' })
}
