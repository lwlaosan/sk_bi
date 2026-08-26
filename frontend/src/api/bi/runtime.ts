import request from '@/utils/request'
import type { BiResponse } from './datasource'

export interface RuntimeControl { key: string; label: string; type: string; required: boolean; defaultValue?: unknown; optionSource: string; targetComponentKeys: string[] }
export interface RuntimeComponent { key: string; name: string; regionType: 'COMPONENT'|'TABLE'; titleVisible: boolean; layout: { x?:number;y?:number;w?:number;h?:number }; rootRoute: RuntimeRoute }
export type RuntimeViewType = 'TABLE'|'BAR'|'STACKED_BAR'|'HORIZONTAL_BAR'|'LINE'|'AREA'|'PIE'|'DONUT'|'GAUGE'|'KPI'
export interface RuntimeRoute { code:string; name:string; viewType:RuntimeViewType; chartConfig?:Record<string,unknown> }
export interface RuntimeConfig { uuid:string; name:string; configVersion:number; maxRows:number; controls:RuntimeControl[]; components:RuntimeComponent[] }
export interface RuntimeField { physicalName:string; displayName:string; dataType:string; visible:boolean; fixedPosition:string; width?:number; alignType?:string; formatPattern?:string; styleIndicatorField?:string; fieldRole?:string; drill?:{targetRouteCode:string;routeValue:string;payloadField:string} }
export interface DrillRequest { routeCode:string; field:string; value:Record<string,unknown> }
export interface QueryRequest { configVersion:number; controls:Record<string,unknown>; drill:DrillRequest|null; requestId:string }
export interface QueryResult { requestId:string; componentKey:string; regionType:string; route:RuntimeRoute; fields:RuntimeField[]; rows:Record<string,unknown>[]; rowCount:number; truncated:boolean; limit:number; elapsedMs:number; traceId:string }
export interface OptionResult { items:{value:string;label:string}[]; truncated:boolean }
export const getRuntimeConfig=(uuid:string):Promise<BiResponse<RuntimeConfig>>=>request({url:`/api/bi/runtime/reports/${uuid}`,method:'get'})
export const getControlOptions=(uuid:string,key:string):Promise<BiResponse<OptionResult>>=>request({url:`/api/bi/runtime/reports/${uuid}/controls/${key}/options`,method:'get'})
export const queryComponent=(uuid:string,key:string,data:QueryRequest):Promise<BiResponse<QueryResult>>=>request({url:`/api/bi/runtime/reports/${uuid}/components/${key}/query`,method:'post',data})
export const exportComponent=(uuid:string,key:string,data:QueryRequest):Promise<Blob>=>request({url:`/api/bi/runtime/reports/${uuid}/components/${key}/export`,method:'post',data,responseType:'blob'}) as unknown as Promise<Blob>
export const exportReport=(uuid:string,data:QueryRequest):Promise<Blob>=>request({url:`/api/bi/runtime/reports/${uuid}/export`,method:'post',data,responseType:'blob'}) as unknown as Promise<Blob>
