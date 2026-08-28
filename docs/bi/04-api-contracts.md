# API 契约

## 1. 通用约定

- API 前缀统一为 `/api/bi`；管理端使用 `/api/bi/admin`，报表运行端使用 `/api/bi/runtime`。
- 所有接口使用 HTTPS、JWT 和 `application/json;charset=UTF-8`，下载接口除外。
- 字段命名使用 `camelCase`；ID 在 JSON 中使用字符串，避免 JavaScript 大整数精度丢失。
- 时间使用带时区 ISO 8601，例如 `2026-08-25T09:30:00+08:00`；日期使用 `YYYY-MM-DD`。
- 管理列表使用服务端分页；运行时组件数据不使用服务端分页。
- `traceId` 由网关或应用生成并贯穿日志，任何错误响应都必须包含。

### 1.1 成功响应

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "01K3H4..."
}
```

### 1.2 错误响应

```json
{
  "code": "BI_CONFIG_VERSION_CONFLICT",
  "message": "配置已被其他用户更新，请重新加载",
  "details": {
    "expectedVersion": 7,
    "actualVersion": 8
  },
  "traceId": "01K3H4..."
}
```

`message` 可展示给用户，但不得包含 JDBC URL、数据库用户名、SQL 文本、过程定义、堆栈或凭据。

## 2. 权限标识

| 权限 | 说明 |
|---|---|
| `bi:datasource:list` | 查看脱敏数据源列表 |
| `bi:datasource:manage` | 新增、修改、测试、停用数据源和 ACL |
| `bi:report:list` | 查看可管理的报表列表 |
| `bi:report:create` | 新建报表 |
| `bi:report:design` | 编辑被授权报表的完整配置 |
| `bi:report:permission` | 管理报表 ACL |
| `bi:report:version` | 查看版本和执行回滚 |
| `bi:report:view` | 进入报表运行页；仍需报表 ACL |
| `bi:report:export` | 导出组件或整报表 |

运行端除了功能权限，还必须校验具体报表 ACL。系统管理员绕过数据源和报表 ACL，但不能绕过认证和审计。

## 3. 数据源管理 API

### 3.1 列表与详情

```http
GET /api/bi/admin/datasources?page=1&pageSize=20&keyword=&status=ENABLED
GET /api/bi/admin/datasources/{datasourceId}
```

详情返回 `hasPassword: true`，不返回密文或掩码密码。编辑时密码留空表示不修改。

### 3.2 新建与修改

```http
POST /api/bi/admin/datasources
PUT  /api/bi/admin/datasources/{datasourceId}
```

```json
{
  "datasourceName": "ERP 生产只读库",
  "databaseType": "MYSQL",
  "host": "mysql.internal",
  "port": 3306,
  "databaseName": "erp_prod",
  "username": "bi_reader",
  "password": "input-only",
  "connectionProps": {
    "useUnicode": true,
    "characterEncoding": "utf8"
  },
  "status": "ENABLED",
  "roleIds": ["101", "102"],
  "userIds": ["2001"]
}
```

`databaseType` 可取 `MYSQL`、`SQLSERVER`、`POSTGRESQL`。SQL Server/PostgreSQL 可在 `connectionProps.schema` 指定 Schema，未指定时分别使用 `dbo`、`public`。类型持久化到现有 JSON 字段，不需要修改项目元数据库。

允许的 `connectionProps` 使用服务端白名单；禁止客户端覆盖 TLS、超时或执行多语句等安全参数。

### 3.3 连接测试

```http
POST /api/bi/admin/datasources/{datasourceId}/test
```

现有数据源使用已保存凭据；创建页面未保存前不提供远程测试，必须先保存。测试结果只返回成功、耗时和错误分类。

### 3.4 存储过程元数据

```http
GET /api/bi/admin/datasources/{datasourceId}/procedures?keyword=sales
GET /api/bi/admin/datasources/{datasourceId}/procedures/{procedureName}/parameters
```

参数响应：

```json
{
  "procedureName": "sp_bi_sales",
  "signatureHash": "sha256...",
  "supported": true,
  "parameters": [
    {
      "ordinal": 1,
      "name": "p_user_id",
      "mode": "IN",
      "mysqlDataType": "varchar",
      "dtdIdentifier": "varchar(64)"
    }
  ],
  "unsupportedReasons": []
}
```

过程名作为路径参数时必须 URL 编码，后端仍按从元数据读取到的精确名称匹配，不参与 SQL 字符串拼接。

## 4. 报表管理 API

### 4.1 报表列表

```http
GET /api/bi/admin/reports?page=1&pageSize=20&keyword=&status=&createdBy=
```

返回名称、UUID、状态、组件数量、当前版本、创建人、更新时间和访问 URL。

### 4.2 新建报表

```http
POST /api/bi/admin/reports
```

```json
{
  "reportName": "销售经营分析",
  "description": "销售汇总和客户明细",
  "defaultDatasourceId": "1001",
  "defaultProcedureName": "sp_bi_sales",
  "maxRows": 50000,
  "timeoutSeconds": 60
}
```

创建成功后生成 UUID、空控件列表、空 ACL、一个 `regionType=TABLE`、`componentKey=main_table` 的默认表格对象和该对象的 `ROOT` 路由。开发者可以停用表格区域并改为只使用上方组件区域，但报表至少保留一个区域。

### 4.3 查询完整设计配置

```http
GET /api/bi/admin/reports/{reportId}/configuration
```

响应为 `ReportDesignConfig`，包含基础信息、ACL、控件、组件、路由、字段、钻取边和参数映射，不包含数据源凭据。

#### 4.3.1 ACL 角色/用户候选项

```http
GET /api/bi/admin/reports/acl-subjects?type=ROLE&keyword=销售
GET /api/bi/admin/reports/acl-subjects?type=USER&keyword=zhang
```

需要 `bi:report:design` 权限。接口最多返回 100 个启用且未删除的候选项，仅包含 `id`、显示名称 `label` 和角色编码/用户账号 `code`，供权限页远程搜索多选使用。

### 4.4 原子保存完整配置

```http
PUT /api/bi/admin/reports/{reportId}/configuration
```

```json
{
  "expectedVersion": 7,
  "changeSummary": "增加客户明细下钻层",
  "baseInfo": {
    "reportName": "销售经营分析",
    "description": "销售汇总和客户明细",
    "status": "ENABLED",
    "defaultDatasourceId": "1001",
    "defaultProcedureName": "sp_bi_sales",
    "defaultSignatureHash": "sha256...",
    "maxRows": 50000,
    "timeoutSeconds": 60
  },
  "acl": {
    "roleIds": ["101"],
    "userIds": ["2001"]
  },
  "controls": [],
  "components": [],
  "parameterMappings": []
}
```

保存采用“完整聚合替换”语义：未出现在请求中的子配置会被删除。后端必须先校验整个聚合，再在事务中写入，不提供半成功响应。

成功响应：

```json
{
  "reportId": "5001",
  "reportUuid": "f2f968d8-c83c-47bf-b088-e51d567a8664",
  "configVersion": 8,
  "effectiveAt": "2026-08-25T10:00:00+08:00"
}
```

### 4.5 校验配置

```http
POST /api/bi/admin/reports/{reportId}/configuration/validate
```

只执行只读校验，不保存配置，也不调用业务存储过程。返回错误和警告：

```json
{
  "valid": false,
  "errors": [
    {
      "path": "components[0].routes[1].drillEdges[0].payloadField",
      "code": "FIELD_NOT_FOUND",
      "message": "载荷字段 F_drill_payload 未定义"
    }
  ],
  "warnings": []
}
```

### 4.6 状态、复制和删除

```http
PUT    /api/bi/admin/reports/{reportId}/status
POST   /api/bi/admin/reports/{reportId}/copy
DELETE /api/bi/admin/reports/{reportId}
```

复制生成新 ID 和 UUID，复制配置但不复制用户 ACL，默认状态为 `DISABLED`。删除为需要二次确认的逻辑删除操作。

## 5. 版本 API

```http
GET  /api/bi/admin/reports/{reportId}/versions?page=1&pageSize=20
GET  /api/bi/admin/reports/{reportId}/versions/{versionNo}
GET  /api/bi/admin/reports/{reportId}/versions/{versionNo}/diff?against={otherVersion}
POST /api/bi/admin/reports/{reportId}/versions/{versionNo}/rollback
```

回滚请求：

```json
{
  "expectedVersion": 12,
  "changeSummary": "回滚错误的字段配置"
}
```

回滚创建版本 13，`operationType=ROLLBACK`、`sourceVersion=7`；版本 7 和 12 均保留。

## 6. 运行配置 API

### 6.1 获取报表运行配置

```http
GET /api/bi/runtime/reports/{uuid}
```

```json
{
  "uuid": "f2f968d8-c83c-47bf-b088-e51d567a8664",
  "name": "销售经营分析",
  "configVersion": 8,
  "maxRows": 50000,
  "controls": [
    {
      "key": "ctrl_date",
      "label": "日期",
      "type": "DATE_RANGE",
      "required": true,
      "defaultValue": {
        "start": "2026-08-01",
        "end": "2026-08-25"
      },
      "optionSource": "NONE",
      "targetComponentKeys": ["sales_summary", "sales_trend", "main_table"]
    }
  ],
  "components": [
    {
      "key": "sales_summary",
      "name": "销售汇总",
      "regionType": "COMPONENT",
      "layout": { "x": 0, "y": 0, "w": 6, "h": 8 },
      "rootRoute": {
        "code": "ROOT",
        "name": "汇总层",
        "viewType": "BAR"
      }
    },
    {
      "key": "main_table",
      "name": "销售明细",
      "regionType": "TABLE",
      "layout": {},
      "rootRoute": {
        "code": "ROOT",
        "name": "汇总层",
        "viewType": "TABLE"
      }
    }
  ]
}
```

`components` 同时承载上方组件和下方表格：`regionType=COMPONENT` 可有多条并按栅格展示，`regionType=TABLE` 最多一条并固定显示在页面下方。运行配置只返回渲染和提交查询所需信息，不返回数据源 ID、过程名、参数映射、SQL、隐藏凭据或无权访问的配置。

### 6.2 控件选项

```http
GET /api/bi/runtime/reports/{uuid}/controls/{controlKey}/options
```

```json
{
  "items": [
    { "value": "S001", "label": "上海一店" }
  ],
  "truncated": false
}
```

SQL 选项最多返回 1,000 项；超过时 `truncated=true`。第一版不接受其他控件值作为查询参数，只允许服务端绑定保留参数 `:currentUserId`。

## 7. 组件查询 API

```http
POST /api/bi/runtime/reports/{uuid}/components/{componentKey}/query
```

该接口统一查询上方组件和下方表格。后端根据保存配置自动绑定：

```text
p_region_key    = COMPONENT 或 TABLE
p_component_key = 路径中的 componentKey
```

`p_component_key` 只定位取数对象，不参与表格下钻判断。只有 `regionType=TABLE` 的请求允许携带 `drill`；上方组件请求的 `drill` 必须为 `null`。

### 7.1 请求

```json
{
  "configVersion": 8,
  "controls": {
    "ctrl_date": {
      "start": "2026-08-01",
      "end": "2026-08-25"
    },
    "ctrl_store": {
      "value": ["S001", "S002"]
    }
  },
  "drill": {
    "routeCode": "STORE_DETAIL",
    "field": "store_detail",
    "value": {
      "saleDate": "2026-08-22",
      "storeId": "S001"
    }
  },
  "requestId": "client-generated-uuid"
}
```

根层查询时：

```json
{
  "configVersion": 8,
  "controls": {},
  "drill": null,
  "requestId": "client-generated-uuid"
}
```

规则：

- `configVersion` 用于发现页面配置已过期。若不是当前版本，返回 `BI_RUNTIME_CONFIG_STALE`，前端重新加载整个运行配置。
- `drill.routeCode` 是目标层级；`drill.field` 是传给 `p_drill_field` 的钻取边 `routeValue`。
- `drill.value` 在 JSON 请求中保持对象，后端规范化序列化后绑定到 `p_drill_value`。
- 服务端根据控件定义过滤未知控件，并拒绝缺失必填控件或错误类型。

### 7.2 响应

```json
{
  "requestId": "client-generated-uuid",
  "componentKey": "main_table",
  "regionType": "TABLE",
  "route": {
    "code": "STORE_DETAIL",
    "name": "门店明细",
    "viewType": "TABLE",
    "chartConfig": null
  },
  "fields": [
    {
      "physicalName": "F_sale_date",
      "displayName": "日期",
      "dataType": "DATE",
      "visible": true,
      "fixedPosition": "LEFT",
      "width": 120,
      "align": "CENTER",
      "formatPattern": "yyyy-MM-dd",
      "styleIndicatorField": null,
      "drill": null
    },
    {
      "physicalName": "F_store_name",
      "displayName": "门店",
      "dataType": "STRING",
      "visible": true,
      "fixedPosition": "NONE",
      "width": 180,
      "align": "LEFT",
      "drill": {
        "targetRouteCode": "ORDER_DETAIL",
        "routeValue": "order_detail",
        "payloadField": "F_order_payload"
      }
    },
    {
      "physicalName": "F_amount",
      "displayName": "销售额",
      "dataType": "NUMBER",
      "visible": true,
      "fixedPosition": "NONE",
      "width": 140,
      "align": "RIGHT",
      "formatPattern": "#,##0.00",
      "styleIndicatorField": "F_amount_style",
      "drill": null
    },
    {
      "physicalName": "F_amount_style",
      "displayName": "",
      "dataType": "STRING",
      "visible": false,
      "fieldRole": "STYLE",
      "drill": null
    },
    {
      "physicalName": "F_order_payload",
      "displayName": "",
      "dataType": "JSON",
      "visible": false,
      "fieldRole": "PAYLOAD",
      "drill": null
    }
  ],
  "rows": [
    {
      "F_sale_date": "2026-08-22",
      "F_store_name": "上海一店",
      "F_amount": 12345.67,
      "F_amount_style": "#FF0000,bold",
      "F_order_payload": {
        "saleDate": "2026-08-22",
        "storeId": "S001"
      }
    }
  ],
  "rowCount": 1,
  "truncated": false,
  "limit": 50000,
  "elapsedMs": 83,
  "traceId": "01K3H4..."
}
```

隐藏字段可以存在于 `rows` 以支持样式和钻取，但 `fields.visible=false`，渲染器不得直接展示。响应不得包含任何配置未引用的结果列。

## 8. 前端核心类型

### 8.1 页面数据洞察

`GET /api/bi/runtime/reports/{uuid}` 的运行配置包含公开入口配置：

```json
{"insight":{"enabled":true,"title":"经营洞察","position":"HEADER"}}
```

`position` 支持 `HEADER`（报表头部）、`FLOAT_RIGHT`（右侧悬浮）和 `BOTTOM`（报表底部）。开发者提示词、供应商和模型名不会通过运行配置接口下发。

`POST /api/bi/runtime/reports/{uuid}/insight` 接收当前浏览器页面已查询出的可见字段和数据行：

```json
{"configVersion":12,"controls":{},"datasets":[{"componentKey":"sales_trend","componentName":"销售趋势","routeName":"根层级","scopeType":"CURRENT","levelPath":["根层级"],"fields":[{"physicalName":"sale_date","displayName":"日期","dataType":"DATE"}],"rows":[{"sale_date":"2026-08-01"}],"rowCount":1,"truncated":false}],"requestId":"uuid"}
```

`scopeType` 为 `CURRENT` 或 `PARENT`，`levelPath` 是从根层级到该数据集的中文路径。服务端从版本化报表快照读取提示词、供应商、模型、行数与 Token 限制；模型成功返回后必须先保存历史，再返回 `historyId/content/provider/model/generatedAt/inputRows/routeSummary/generatedByName`。浏览器不得提交或覆盖 API Key、提示词、模型或上游地址。

洞察历史接口（需要报表查看权限，并继续执行该报表 ACL）：

- `GET /api/bi/runtime/reports/{uuid}/insights?page=1&pageSize=20`：按生成时间倒序分页。
- `GET /api/bi/runtime/reports/{uuid}/insights/{historyId}`：返回 Markdown 正文、生成者、模型、报表配置版本、层级摘要和当时发送给模型的数据快照。

历史记录不可修改；每次成功洞察保存一条。前端以 GFM 解析正文，并在写入 DOM 前进行 HTML 清洗。

模型密钥管理接口（需要 `bi:datasource:manage` 权限）：

- `GET /api/bi/admin/insight/providers`：返回供应商、是否配置、来源、脱敏末四位和凭据版本。
- `PUT /api/bi/admin/insight/providers/{provider}/credential`：请求体为 `{"apiKey":"..."}`，加密保存或替换密钥。
- `DELETE /api/bi/admin/insight/providers/{provider}/credential`：清除页面保存的密钥；若存在环境变量则自动回退。

任何响应均不得返回完整 API Key，审计日志不得记录请求体。

```ts
export type ViewType = 'TABLE' | 'BAR' | 'STACKED_BAR' | 'HORIZONTAL_BAR' | 'LINE' | 'AREA' | 'PIE' | 'DONUT' | 'GAUGE' | 'KPI';
export type RegionType = 'COMPONENT' | 'TABLE';
export type FieldDataType = 'STRING' | 'NUMBER' | 'DATE' | 'DATETIME' | 'BOOLEAN' | 'JSON';

export interface DrillRequest {
  routeCode: string;
  field: string;
  value: Record<string, unknown>;
}

export interface ComponentQueryRequest {
  configVersion: number;
  controls: Record<string, ControlValue>;
  drill: DrillRequest | null;
  requestId: string;
}

export interface ComponentQueryResult {
  requestId: string;
  componentKey: string;
  regionType: RegionType;
  route: RuntimeRoute;
  fields: RuntimeField[];
  rows: Array<Record<string, unknown>>;
  rowCount: number;
  truncated: boolean;
  limit: number;
  elapsedMs: number;
  traceId: string;
}

export interface DrillHistoryItem {
  routeCode: string;
  routeName: string;
  label: string;
  request: DrillRequest | null;
}
```

面包屑保存在组件运行状态中，不由后端 Session 保存，保证 API 无状态。

## 9. 错误码

| HTTP | code | 场景 |
|---:|---|---|
| 400 | `BI_REQUEST_INVALID` | JSON、控件值或字段格式错误 |
| 400 | `BI_CONFIG_INVALID` | 配置引用或结构不合法 |
| 400 | `BI_SP_PARAMETER_UNSUPPORTED` | OUT/INOUT、多结果集或不支持类型 |
| 401 | `UNAUTHORIZED` | 未登录或令牌失效 |
| 403 | `BI_REPORT_FORBIDDEN` | 无报表 ACL |
| 403 | `BI_DATASOURCE_FORBIDDEN` | 无数据源使用权限 |
| 404 | `BI_REPORT_NOT_FOUND` | UUID 不存在、已删除或为避免泄露而隐藏 |
| 404 | `BI_COMPONENT_NOT_FOUND` | 组件键不存在 |
| 422 | `BI_DRILL_NOT_ALLOWED` | 上方组件携带钻取请求或表格钻取边无效 |
| 409 | `BI_CONFIG_VERSION_CONFLICT` | 管理端保存乐观锁冲突 |
| 409 | `BI_RUNTIME_CONFIG_STALE` | 运行页面使用旧配置版本 |
| 409 | `BI_SP_SIGNATURE_CHANGED` | 存储过程签名变化 |
| 422 | `BI_RESULT_FIELD_MISSING` | 结果集缺少配置字段 |
| 422 | `BI_CONTROL_OPTION_SQL_INVALID` | 控件 SQL 不符合只读规则 |
| 429 | `BI_QUERY_BUSY` | 超过用户或平台并发限制 |
| 500 | `BI_QUERY_FAILED` | 存储过程执行失败，详情仅入服务端日志 |
| 503 | `BI_DATASOURCE_UNAVAILABLE` | 数据源不可连接或连接池不可用 |
| 504 | `BI_QUERY_TIMEOUT` | 查询超过配置超时 |
| 400 | `BI_INSIGHT_REQUEST_INVALID` | 洞察页面数据不合法 |
| 404 | `BI_INSIGHT_DISABLED` | 报表未启用洞察 |
| 429 | `BI_INSIGHT_CONCURRENCY_LIMIT` | 洞察调用达到平台并发上限 |
| 502 | `BI_INSIGHT_UPSTREAM_FAILED` | 模型服务调用失败 |
| 503 | `BI_INSIGHT_PROVIDER_UNAVAILABLE` | 服务端未配置供应商 API Key |
| 504 | `BI_INSIGHT_TIMEOUT` | 模型服务响应超时 |

## 10. 幂等与取消

- 查询是只读操作，`requestId` 用于前端防止乱序覆盖和日志关联，不作为服务端结果缓存键。
- 保存配置依靠 `expectedVersion` 保证并发安全。
- 前端取消 HTTP 请求后，服务端尽力调用 JDBC `Statement.cancel()`；取消失败时允许数据库请求完成，但不返回给已断开的客户端。
