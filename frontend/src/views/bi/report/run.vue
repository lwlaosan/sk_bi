<template>
  <div v-loading="loading" class="runtime-page">
    <header class="runtime-header"><h2>{{ config?.name || 'BI 报表' }}</h2><span class="spacer"/><el-button v-if="showInsightAt('HEADER')" type="success" plain :disabled="!hasResults" :loading="insightLoading" @click="openInsight">{{ config?.insight?.title }}</el-button><el-button :disabled="!hasResults" @click="downloadAll">整报表导出</el-button><el-button @click="print">打印</el-button><el-button type="primary" :loading="querying" @click="queryAll">查询</el-button></header>
    <section v-if="config?.controls.length" class="control-bar">
      <div v-for="control in config.controls" :key="control.key" class="control-item"><label>{{ control.label }}<b v-if="control.required">*</b></label>
        <el-date-picker v-if="control.type==='DATE_RANGE'" v-model="values[control.key]" type="daterange" value-format="YYYY-MM-DD" />
        <el-date-picker v-else-if="control.type==='DATE'" v-model="values[control.key]" value-format="YYYY-MM-DD" />
        <el-input-number v-else-if="control.type==='NUMBER'" v-model="values[control.key]" />
        <div v-else-if="control.type==='NUMBER_RANGE'" class="range"><el-input-number v-model="values[control.key].min"/><span>—</span><el-input-number v-model="values[control.key].max"/></div>
        <el-select v-else-if="['SINGLE_SELECT','MULTI_SELECT'].includes(control.type)" v-model="values[control.key]" :multiple="control.type==='MULTI_SELECT'" filterable clearable><el-option v-for="item in options[control.key]||[]" :key="item.value" :label="item.label" :value="item.value"/></el-select>
        <el-input v-else v-model="values[control.key]" clearable />
      </div>
    </section>
    <el-alert v-if="globalError" type="error" :closable="false" :title="globalError" class="mb12"/>
    <section class="component-grid">
      <article v-for="component in upper" :key="component.key" class="component-card" :style="gridStyle(component)">
        <header v-if="component.titleVisible"><strong>{{ component.name }}</strong><span v-if="states[component.key]?.result" class="elapsed">{{ states[component.key].result?.elapsedMs }}ms</span></header>
        <div v-loading="states[component.key]?.loading" class="component-body"><el-empty v-if="states[component.key]?.error" :description="states[component.key].error"/>
          <div v-else-if="states[component.key]?.result?.route.viewType==='KPI'" class="kpi">{{ kpiValue(component.key) }}</div>
          <div v-else :ref="setChartRef(component.key)" class="chart"></div></div>
      </article>
    </section>
    <article v-for="component in tables" :key="component.key" class="table-card">
      <header><strong>{{ component.name }}</strong><span class="spacer"/><el-button size="small" :disabled="!states[component.key]?.result" @click="download(component.key)">导出 Excel</el-button></header>
      <el-breadcrumb v-if="states[component.key]?.history.length" separator="/" class="breadcrumb"><el-breadcrumb-item v-for="(item,index) in states[component.key].history" :key="index"><a @click="back(component.key,index)">{{ item.label }}</a></el-breadcrumb-item></el-breadcrumb>
      <el-alert v-if="states[component.key]?.result?.truncated" type="warning" :closable="false" title="结果已达到行数上限，分页、排序和导出均基于当前已加载数据。"/>
      <el-alert v-if="states[component.key]?.error" type="error" :closable="false" :title="states[component.key].error"/>
      <el-table v-loading="states[component.key]?.loading" :data="pageRows(component.key)" border height="520" @sort-change="event => sort(component.key, event)" @cell-click="(row, column) => cellClick(component.key, row, column)">
        <el-table-column v-for="field in visibleFields(component.key)" :key="field.physicalName" :prop="field.physicalName" :label="field.displayName" :min-width="field.width || 100" :fixed="fixed(field.fixedPosition)" sortable="custom" :align="(field.alignType||'LEFT').toLowerCase() as any">
          <template #default="scope"><span :class="{drillable:!!field.drill}" :style="styleFor(scope.row,field)">{{ display(scope.row[field.physicalName],field) }}</span></template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="states[component.key]?.result" v-model:current-page="states[component.key].page" v-model:page-size="states[component.key].pageSize" layout="total, sizes, prev, pager, next" :total="states[component.key].result?.rowCount||0" :page-sizes="[20,50,100,200]"/>
    </article>
    <div v-if="showInsightAt('BOTTOM')" class="insight-bottom"><el-button type="success" :disabled="!hasResults" :loading="insightLoading" @click="openInsight">{{ config?.insight?.title }}</el-button></div>
    <el-button v-if="showInsightAt('FLOAT_RIGHT')" class="insight-float" type="success" :disabled="!hasResults" :loading="insightLoading" @click="openInsight">{{ config?.insight?.title }}</el-button>
    <el-drawer v-model="insightOpen" :title="config?.insight?.title || '智能洞察'" :size="insightDrawerSize">
      <el-segmented v-model="insightMode" :options="[{label:'当前洞察',value:'CURRENT'},{label:'历史记录',value:'HISTORY'}]" class="insight-switch" @change="modeChanged" />
      <template v-if="insightMode==='CURRENT'">
        <el-alert v-if="insightStale && insightContent" type="warning" :closable="false" title="页面数据已变化，请重新生成洞察。" class="mb12" />
        <div v-if="insightContent" class="insight-content" v-html="renderedInsight"></div>
        <el-empty v-else-if="!insightLoading" description="点击下方按钮，根据当前页面数据生成洞察" />
        <div v-if="insightMeta" class="insight-meta">{{ insightMeta }}</div>
        <el-collapse v-if="insightContext" class="context-collapse"><el-collapse-item title="查看本次洞察的数据快照"><pre>{{ prettyContext }}</pre></el-collapse-item></el-collapse>
      </template>
      <template v-else>
        <div v-loading="historyLoading" class="history-list">
          <el-empty v-if="!historyItems.length && !historyLoading" description="暂无历史洞察" />
          <button v-for="item in historyItems" :key="item.id" type="button" class="history-item" @click="viewHistory(item.id)">
            <strong>{{ item.routeSummary || '报表当前层级' }}</strong>
            <span>{{ item.generatedBy }} · {{ formatTime(item.generatedAt) }}</span>
            <span>{{ item.provider }} / {{ item.model }} · {{ item.inputRows }} 行</span>
          </button>
          <el-pagination v-if="historyTotal>historyPageSize" v-model:current-page="historyPage" layout="prev, pager, next" :page-size="historyPageSize" :total="historyTotal" @current-change="loadHistory" />
        </div>
      </template>
      <template #footer><el-button @click="insightOpen=false">关闭</el-button><el-button v-if="insightMode==='CURRENT'" type="primary" :loading="insightLoading" :disabled="!hasResults" @click="generate">生成洞察</el-button></template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { saveAs } from 'file-saver'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getRuntimeConfig,getControlOptions,queryComponent,exportComponent,exportReport,generateInsight,getInsightHistory,getInsightHistoryDetail,type RuntimeConfig,type RuntimeComponent,type RuntimeControl,type RuntimeField,type QueryResult,type DrillRequest,type InsightDataset,type InsightHistorySummary } from '@/api/bi/runtime'

interface State { loading:boolean; error:string; result?:QueryResult; drill:DrillRequest|null; history:{label:string;drill:DrillRequest|null;result?:QueryResult}[]; page:number; pageSize:number; sort?:{prop:string;order:string} }
const route=useRoute(),uuid=String(route.params.uuid),loading=ref(true),querying=ref(false),globalError=ref(''),config=ref<RuntimeConfig>(),values=reactive<Record<string,any>>({}),options=reactive<Record<string,{value:string;label:string}[]>>({}),states=reactive<Record<string,State>>({}),charts=new Map<string,echarts.ECharts>(),chartElements=new Map<string,HTMLElement>(),chartObservers=new Map<string,ResizeObserver>()
const insightOpen=ref(false),insightLoading=ref(false),insightContent=ref(''),insightMeta=ref(''),insightStale=ref(false),insightMode=ref<'CURRENT'|'HISTORY'>('CURRENT'),insightContext=ref<Record<string,unknown>|null>(null)
const historyItems=ref<InsightHistorySummary[]>([]),historyLoading=ref(false),historyPage=ref(1),historyPageSize=20,historyTotal=ref(0)
const renderedInsight=computed(()=>DOMPurify.sanitize(marked.parse(insightContent.value,{async:false}) as string))
const prettyContext=computed(()=>JSON.stringify(insightContext.value,null,2))
const insightDrawerSize=computed(()=>window.matchMedia('(max-width: 768px)').matches?'92%':'680px')
const upper=computed(()=>config.value?.components.filter((c:RuntimeComponent)=>c.regionType==='COMPONENT')||[]),tables=computed(()=>config.value?.components.filter((c:RuntimeComponent)=>c.regionType==='TABLE')||[])
const hasResults=computed(()=>(Object.values(states) as State[]).some((state:State)=>!!state.result))
async function load(){try{config.value=(await getRuntimeConfig(uuid)).data;for(const c of config.value.components)states[c.key]={loading:false,error:'',drill:null,history:[{label:'根层级',drill:null}],page:1,pageSize:50};for(const c of config.value.controls){values[c.key]=defaultValue(c.type,c.defaultValue);if(c.optionSource!=='NONE')getControlOptions(uuid,c.key).then((r:{data:{items:{value:string;label:string}[]}})=>options[c.key]=r.data.items)}if(requiredReady())await queryAll()}catch(e:any){globalError.value=e?.message||'报表加载失败'}finally{loading.value=false}}
function defaultValue(type:string,value:unknown){if(value!==undefined&&value!==null)return value;if(type==='MULTI_SELECT')return[];if(type.endsWith('_RANGE'))return type==='DATE_RANGE'?[]:{min:null,max:null};return null}
function requiredReady(){return !!config.value?.controls.every((c:RuntimeControl)=>!c.required||(values[c.key]!==null&&values[c.key]!==''&&(!Array.isArray(values[c.key])||values[c.key].length>0)))}
function controlPayload(){const out:Record<string,unknown>={};for(const c of config.value?.controls||[]){const v=values[c.key];if(v===null||v===''||v===undefined)continue;if(c.type==='DATE_RANGE')out[c.key]={start:v[0],end:v[1]};else if(c.type==='NUMBER_RANGE')out[c.key]=v;else out[c.key]={value:v}}return out}
async function queryOne(key:string){const state=states[key];state.loading=true;state.error='';const requestId=crypto.randomUUID();try{const result=(await queryComponent(uuid,key,{configVersion:config.value!.configVersion,controls:controlPayload(),drill:state.drill,requestId})).data;if(result.requestId===requestId){state.result=result;state.history[state.history.length-1].result=result;state.page=1;insightStale.value=!!insightContent.value;await nextTick();renderChart(key)}}catch(e:any){state.error=e?.message||'查询失败'}finally{state.loading=false}}
async function queryAll(){if(querying.value)return;if(!requiredReady()){globalError.value='请填写必填查询条件';return}querying.value=true;globalError.value='';try{for(const component of config.value?.components||[]){states[component.key].drill=null;states[component.key].history=[{label:'根层级',drill:null}]}const all=config.value?.components||[],batchSize=window.matchMedia('(max-width: 768px)').matches?1:2;for(let i=0;i<all.length;i+=batchSize)await Promise.all(all.slice(i,i+batchSize).map((c:RuntimeComponent)=>queryOne(c.key)))}finally{querying.value=false}}
function visibleFields(key:string){return states[key]?.result?.fields.filter((f:RuntimeField)=>f.visible)||[]}
function sortedRows(key:string){const state=states[key],rows=[...(state?.result?.rows||[])];if(state?.sort?.prop)rows.sort((a,b)=>{const x=a[state.sort!.prop],y=b[state.sort!.prop],n=x==y?0:x==null?-1:y==null?1:(x as any)<(y as any)?-1:1;return state.sort!.order==='descending'?-n:n});return rows}
function pageRows(key:string){const s=states[key];return sortedRows(key).slice((s.page-1)*s.pageSize,s.page*s.pageSize)}
function sort(key:string,e:{prop:string;order:string}){states[key].sort=e?.order?e:undefined}
function fixed(value:string){return value==='NONE'?false:value.toLowerCase() as 'left'|'right'}
function display(value:unknown,field:RuntimeField){if(value==null)return '';if(typeof value==='object')return JSON.stringify(value);if(field.dataType==='NUMBER'&&typeof value==='number'){const match=/\.([0#]+)/.exec(field.formatPattern||'');const digits=match?.[1].length;return new Intl.NumberFormat('zh-CN',digits===undefined?{maximumFractionDigits:6}:{minimumFractionDigits:digits,maximumFractionDigits:digits}).format(value)}return String(value)}
function styleFor(row:Record<string,unknown>,field:RuntimeField){const raw=field.styleIndicatorField?String(row[field.styleIndicatorField]||''):'';const match=/^(#[0-9A-Fa-f]{6}),(bold|normal)$/.exec(raw);return match?{color:match[1],fontWeight:match[2]}:{}}
async function drill(key:string,row:Record<string,unknown>,property:string){const field=visibleFields(key).find((f:RuntimeField)=>f.physicalName===property);if(!field?.drill)return;const payload=row[field.drill.payloadField];if(!payload||typeof payload!=='object')return;const request={routeCode:field.drill.targetRouteCode,field:field.drill.routeValue,value:payload as Record<string,unknown>};states[key].drill=request;states[key].history.push({label:String(row[property]??field.drill.targetRouteCode),drill:request});await queryOne(key)}
function cellClick(key:string,row:Record<string,unknown>,column:{property:string}){return drill(key,row,column.property)}
async function back(key:string,index:number){const s=states[key];s.history=s.history.slice(0,index+1);s.drill=s.history[index].drill;await queryOne(key)}
function gridStyle(c:RuntimeComponent){const height=Math.max(220,Math.min(800,(c.layout?.h||8)*40));return {gridColumn:`span ${Math.max(1,Math.min(12,c.layout?.w||6))}`,'--component-body-height':`${height}px`}}
function setChartRef(key:string){return (el:any)=>{if(el)chartElements.set(key,el as HTMLElement);else chartElements.delete(key)}}
function chartFor(key:string){
  const el=chartElements.get(key);if(!el||el.clientWidth===0||el.clientHeight===0)return
  let chart=charts.get(key)
  if(!chart||chart.getDom()!==el){chart?.dispose();chart=echarts.init(el);charts.set(key,chart);chartObservers.get(key)?.disconnect();const observer=new ResizeObserver(()=>chart?.resize());observer.observe(el);chartObservers.set(key,observer)}
  else chart.resize()
  return chart
}
function renderChart(key:string){
  const result=states[key]?.result;if(!result||result.route.viewType==='KPI')return
  const chart=chartFor(key);if(!chart)return
  const visible=result.fields.filter((f:RuntimeField)=>f.visible)
  const dimension=visible.find((f:RuntimeField)=>f.fieldRole==='DIMENSION')||visible.find((f:RuntimeField)=>f.dataType!=='NUMBER')||visible[0]
  const measures=visible.filter((f:RuntimeField)=>['MEASURE','VALUE'].includes(f.fieldRole||'')&&f.physicalName!==dimension?.physicalName).slice(0,5)
  const categories=result.rows.map((row:Record<string,unknown>)=>row[dimension?.physicalName])
  const type=result.route.viewType
  if(['PIE','DONUT'].includes(type)){
    const measure=measures[0];chart.setOption({tooltip:{trigger:'item'},legend:{type:'scroll',bottom:0},series:[{name:measure?.displayName,type:'pie',radius:type==='DONUT'?['42%','68%']:'68%',data:result.rows.map((row:Record<string,unknown>)=>({name:String(row[dimension?.physicalName]??''),value:Number(row[measure?.physicalName]??0)})),label:{show:window.innerWidth>600,formatter:'{b}: {d}%'}}]},true);return
  }
  if(type==='GAUGE'){
    const measure=measures[0]||visible.find((f:RuntimeField)=>f.dataType==='NUMBER');const value=Number(result.rows[0]?.[measure?.physicalName]??0);chart.setOption({series:[{type:'gauge',progress:{show:true},detail:{valueAnimation:true,formatter:'{value}'},data:[{value,name:measure?.displayName||''}]}]},true);return
  }
  const horizontal=type==='HORIZONTAL_BAR',line=['LINE','AREA'].includes(type)
  const categoryAxis={type:'category' as const,data:categories,axisLabel:{hideOverlap:true}}
  const valueAxis={type:'value' as const}
  chart.setOption({tooltip:{trigger:'axis'},legend:{},grid:{left:64,right:24,top:48,bottom:48,containLabel:true},xAxis:horizontal?valueAxis:categoryAxis,yAxis:horizontal?categoryAxis:valueAxis,series:measures.map((m:RuntimeField)=>({name:m.displayName,type:line?'line':'bar',smooth:line,stack:type==='STACKED_BAR'?'total':undefined,areaStyle:type==='AREA'?{}:undefined,data:result.rows.map((row:Record<string,unknown>)=>row[m.physicalName])}))},true)
}
function kpiValue(key:string){const r=states[key]?.result,f=r?.fields.find((x:RuntimeField)=>x.visible&&['VALUE','MEASURE'].includes(x.fieldRole||''))||r?.fields.find((x:RuntimeField)=>x.visible);return r&&f?display(r.rows[0]?.[f.physicalName],f):'—'}
function showInsightAt(position:'HEADER'|'FLOAT_RIGHT'|'BOTTOM'){return !!config.value?.insight?.enabled&&config.value.insight.position===position}
function openInsight(){insightOpen.value=true}
function dataset(component:RuntimeComponent,result:QueryResult,scopeType:'CURRENT'|'PARENT',levelPath:string[]):InsightDataset{const fields=result.fields.filter((field:RuntimeField)=>field.visible).map((field:RuntimeField)=>({physicalName:field.physicalName,displayName:field.displayName,dataType:field.dataType}));return {componentKey:component.key,componentName:component.name,routeName:result.route.name,scopeType,levelPath,fields,rows:result.rows.map(row=>Object.fromEntries(fields.map(field=>[field.physicalName,row[field.physicalName]]))),rowCount:result.rowCount,truncated:result.truncated}}
function insightDatasets():InsightDataset[]{return (config.value?.components||[]).flatMap(component=>{const history=states[component.key]?.history||[];return history.flatMap((level,index)=>level.result?[dataset(component,level.result,index===history.length-1?'CURRENT':'PARENT',history.slice(0,index+1).map(item=>item.label))]:[])})}
async function generate(){if(!config.value||insightLoading.value)return;insightLoading.value=true;try{const requestId=crypto.randomUUID();const result=(await generateInsight(uuid,{configVersion:config.value.configVersion,controls:controlPayload(),datasets:insightDatasets(),requestId})).data;if(result.requestId===requestId){insightContent.value=result.content;insightMeta.value=`${result.generatedByName} · ${result.routeSummary} · ${result.provider}/${result.model} · ${result.inputRows} 行 · ${formatTime(result.generatedAt)}`;insightContext.value=null;insightStale.value=false;historyPage.value=1;await loadHistory()}}catch(e:any){ElMessage.error(e?.message||'洞察生成失败')}finally{insightLoading.value=false}}
function formatTime(value:string){return new Date(value).toLocaleString('zh-CN')}
async function modeChanged(value:string|number|boolean){if(value==='HISTORY')await loadHistory()}
async function loadHistory(){historyLoading.value=true;try{const data=(await getInsightHistory(uuid,historyPage.value,historyPageSize)).data;historyItems.value=data.items;historyTotal.value=data.total}catch(e:any){ElMessage.error(e?.message||'历史洞察加载失败')}finally{historyLoading.value=false}}
async function viewHistory(id:string){historyLoading.value=true;try{const item=(await getInsightHistoryDetail(uuid,id)).data;insightContent.value=item.content;insightMeta.value=`${item.generatedBy} · ${item.routeSummary} · ${item.provider}/${item.model} · ${item.inputRows} 行 · ${formatTime(item.generatedAt)} · 配置 v${item.configVersion}`;insightContext.value=item.contextSnapshot;insightStale.value=false;insightMode.value='CURRENT'}catch(e:any){ElMessage.error(e?.message||'历史洞察加载失败')}finally{historyLoading.value=false}}
function exportFilename(){const name=(config.value?.name||'报表').replace(/[\\/:*?"<>|]/g,'_');const now=new Date(),pad=(value:number)=>String(value).padStart(2,'0');const stamp=`${now.getFullYear()}${pad(now.getMonth()+1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;return `${name}-${stamp}.xlsx`}
async function download(key:string){const blob=await exportComponent(uuid,key,{configVersion:config.value!.configVersion,controls:controlPayload(),drill:states[key].drill,requestId:crypto.randomUUID()});saveAs(blob,exportFilename())}
async function downloadAll(){const blob=await exportReport(uuid,{configVersion:config.value!.configVersion,controls:controlPayload(),drill:null,requestId:crypto.randomUUID()});saveAs(blob,exportFilename())}
function print(){window.print()}
onBeforeUnmount(()=>{chartObservers.forEach(observer=>observer.disconnect());charts.forEach(c=>c.dispose())});load()
</script>

<style scoped>
.runtime-page{padding:18px;background:#f4f6f9;min-height:calc(100vh - 84px)}.runtime-header,.table-card>header,.component-card>header{display:flex;align-items:center;gap:12px}.runtime-header h2{margin:0}.spacer{flex:1}.control-bar{display:flex;flex-wrap:wrap;gap:16px;padding:16px;margin:16px 0;background:#fff;border-radius:6px}.control-item{min-width:220px}.control-item label{display:block;margin-bottom:7px;color:#606266}.control-item b{color:#f56c6c}.control-item :deep(.el-select),.control-item :deep(.el-input){width:220px}.range{display:flex;align-items:center;gap:6px}.component-grid{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));gap:14px;align-items:start}.component-card,.table-card{background:#fff;border-radius:6px;padding:14px;margin-bottom:14px;min-width:0}.component-body,.chart{height:var(--component-body-height,300px)}.elapsed{margin-left:auto;color:#909399}.kpi{display:flex;height:100%;align-items:center;justify-content:center;padding:0 12px;font-size:clamp(28px,3.2vw,42px);font-weight:700;white-space:nowrap;overflow:hidden}.table-card{margin-top:14px}.breadcrumb{margin:12px 0}.drillable{color:#409eff;cursor:pointer;text-decoration:underline}.mb12{margin:12px 0}.el-pagination{justify-content:flex-end;margin-top:12px}.insight-float{position:fixed;right:24px;top:50%;z-index:20;box-shadow:0 5px 18px rgba(0,0,0,.18)}.insight-bottom{display:flex;justify-content:center;padding:14px}.insight-switch{width:100%;margin-bottom:18px}.insight-content{line-height:1.75;font-size:15px;color:#303133;overflow-wrap:anywhere}.insight-content :deep(h1),.insight-content :deep(h2),.insight-content :deep(h3){margin:22px 0 12px;line-height:1.35}.insight-content :deep(p){margin:10px 0}.insight-content :deep(table){width:100%;border-collapse:collapse;display:block;overflow-x:auto}.insight-content :deep(th),.insight-content :deep(td){border:1px solid #dcdfe6;padding:8px 10px;white-space:nowrap}.insight-content :deep(th){background:#f5f7fa}.insight-content :deep(code){padding:2px 5px;background:#f5f7fa;border-radius:3px}.insight-meta{margin-top:20px;padding-top:12px;border-top:1px solid #ebeef5;color:#909399;font-size:12px}.context-collapse{margin-top:16px}.context-collapse pre{max-height:360px;overflow:auto;white-space:pre-wrap;background:#f5f7fa;padding:12px;border-radius:6px;font-size:12px}.history-list{min-height:180px}.history-item{display:flex;width:100%;flex-direction:column;gap:7px;text-align:left;border:1px solid #e4e7ed;background:#fff;border-radius:6px;padding:13px;margin-bottom:10px;cursor:pointer;color:#606266}.history-item:hover{border-color:#409eff;background:#f5f9ff}.history-item strong{color:#303133}.history-item span{font-size:12px}@media(max-width:768px){.runtime-page{padding:10px;min-height:100vh}.runtime-header{flex-wrap:wrap;gap:8px}.runtime-header h2{width:100%;font-size:20px}.runtime-header .spacer{display:none}.runtime-header .el-button{flex:1;margin-left:0}.control-bar{display:block;padding:12px;margin:10px 0}.control-item{min-width:0;width:100%;margin-bottom:12px}.control-item:last-child{margin-bottom:0}.control-item :deep(.el-select),.control-item :deep(.el-input),.control-item :deep(.el-date-editor){width:100%!important}.component-grid{display:block}.component-card{grid-column:1/-1!important;padding:12px}.component-body,.chart{height:min(var(--component-body-height,300px),360px)}.kpi{font-size:clamp(26px,10vw,38px)}.table-card{padding:12px}.table-card>header{flex-wrap:wrap}.el-pagination{justify-content:center;overflow:auto}.elapsed{font-size:12px}.insight-float{right:12px;bottom:18px;top:auto}}@media print{.runtime-header button,.control-bar,.el-pagination,.table-card>header button,.insight-float,.insight-bottom{display:none!important}.runtime-page{padding:0;background:#fff}.component-card,.table-card{break-inside:avoid;box-shadow:none}}
</style>
