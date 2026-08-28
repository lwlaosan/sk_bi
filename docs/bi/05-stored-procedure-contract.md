# 多数据库存储过程契约

## 1. 目标

本规范定义 BI 运行引擎如何发现、配置和调用 MySQL 8、SQL Server 与 PostgreSQL 存储过程，以及存储过程如何返回可被动态组件渲染的结果集。

核心原则：存储过程决定“返回什么业务数据”，配置决定“如何调用和展示”，前端不推断数据库业务逻辑。

## 2. 支持边界

- 支持 MySQL 8、SQL Server 和 PostgreSQL 存储过程；数据库类型保存在现有数据源 `connection_props` 中，不修改系统元数据库表结构。
- SQL Server、PostgreSQL 使用 `schema.procedure` 名称；默认 Schema 分别为 `dbo`、`public`。
- 仅支持 `IN` 参数和一个结果集。
- 不支持 `OUT`、`INOUT`、多个结果集、动态 JDBC URL 或客户端指定过程名。
- 过程应为只读查询。平台账号应采用最小权限，避免过程通过调用者权限修改业务数据。
- 同一过程可以通过 `p_region_key`、`p_component_key` 和 `p_drill_field` 返回不同结构。
- 上方组件与下方表格可以返回完全不相关的数据，也可以分别覆盖报表默认过程；覆盖过程拥有独立签名和参数映射。

## 3. 推荐参数

以下是保留名称。数据库中的实际顺序由存储过程签名决定，系统从元数据读取并按序绑定，不依赖硬编码位置。

| 参数 | 推荐类型 | 来源 | 根层值 | 说明 |
|---|---|---|---|---|
| `p_user_id` | `VARCHAR(64)` | `SYSTEM:user_id` | 当前内部用户 ID | 行级权限，客户端不可覆盖 |
| `p_region_key` | `VARCHAR(16)` | `REGION:region_key` | `COMPONENT`/`TABLE` | 区分上方组件区域和下方表格区域 |
| `p_component_key` | `VARCHAR(64)` | `COMPONENT:component_key` | 当前对象键 | 在区域内定位具体取数对象，与下钻无关 |
| `p_drill_field` | `VARCHAR(64)` | `DRILL:field` | `''` | 钻取边的稳定路由值 |
| `p_drill_value` | `JSON` 或 `LONGTEXT` | `DRILL:value` | `NULL` | 当前钻取上下文 JSON |

查询控件参数由开发者命名，例如 `p_date_start`、`p_date_end`、`p_store_ids`。推荐以 `p_` 开头，但系统以元数据中的精确名称为准。

### 3.1 为什么保留 `p_drill_field` 和 `p_drill_value`

它们与旧系统的 `p_col_name` 和 `p_col_id` 角色等价，但含义更清晰：

- `trigger_field` 是页面上哪一列可点击，只存在于报表配置，不直接作为过程参数。
- `p_drill_field` 表示执行哪个业务分支，例如 `store_detail`。
- `p_drill_value` 是该分支需要的 JSON 数据，例如日期和门店 ID。

这样不会把显示列名、过程分支和点击值混成同一个概念。

### 3.2 与旧 `part_id` 的关系

旧系统的 `part_id='1,1'` 同时编码数据区域和展示类型。新协议将其拆开：

```text
p_region_key    = 'COMPONENT' 或 'TABLE'   -- 数据区域
p_component_key = 'sales_chart' 或 'main_table' -- 具体取数对象
view_type       = 'BAR'/'STACKED_BAR'/'HORIZONTAL_BAR'/'LINE'/'AREA'/'PIE'/'DONUT'/'GAUGE'/'KPI'/'TABLE' -- 只存在配置中，不传数据库
```

如果必须保留旧参数名，可以把 `p_part_id` 映射为 `REGION:region_key`，但不再使用逗号拼接展示类型。`p_component_key` 在表格从 ROOT 下钻到任何层级时保持不变。

## 4. 参数元数据发现

读取参数使用参数化查询：

```sql
SELECT
    ORDINAL_POSITION,
    PARAMETER_MODE,
    PARAMETER_NAME,
    DATA_TYPE,
    DTD_IDENTIFIER
FROM INFORMATION_SCHEMA.PARAMETERS
WHERE SPECIFIC_SCHEMA = ?
  AND SPECIFIC_NAME = ?
ORDER BY ORDINAL_POSITION;
```

签名规范化字符串示例：

```text
1|IN|p_user_id|varchar|varchar(64)
2|IN|p_region_key|varchar|varchar(16)
3|IN|p_component_key|varchar|varchar(64)
4|IN|p_date_start|date|date
5|IN|p_date_end|date|date
6|IN|p_store_ids|json|json
7|IN|p_drill_field|varchar|varchar(64)
8|IN|p_drill_value|json|json
```

对 UTF-8 规范化文本计算 SHA-256，保存为 `signatureHash`。以下任一变化都视为签名变化：参数增删、顺序变化、名称变化、模式变化或 `DTD_IDENTIFIER` 变化。

查询前签名变化时返回 `BI_SP_SIGNATURE_CHANGED`，不得猜测新参数或继续使用旧映射。

## 5. 参数来源映射

示例：

```json
[
  {
    "ordinal": 1,
    "name": "p_user_id",
    "mysqlDataType": "varchar",
    "sourceType": "SYSTEM",
    "sourceKey": "user_id"
  },
  {
    "ordinal": 2,
    "name": "p_region_key",
    "mysqlDataType": "varchar",
    "sourceType": "REGION",
    "sourceKey": "region_key"
  },
  {
    "ordinal": 3,
    "name": "p_component_key",
    "mysqlDataType": "varchar",
    "sourceType": "COMPONENT",
    "sourceKey": "component_key"
  },
  {
    "ordinal": 4,
    "name": "p_date_start",
    "mysqlDataType": "date",
    "sourceType": "CONTROL",
    "sourceKey": "ctrl_date.start"
  },
  {
    "ordinal": 5,
    "name": "p_date_end",
    "mysqlDataType": "date",
    "sourceType": "CONTROL",
    "sourceKey": "ctrl_date.end"
  },
  {
    "ordinal": 6,
    "name": "p_store_ids",
    "mysqlDataType": "json",
    "sourceType": "CONTROL",
    "sourceKey": "ctrl_store.value"
  },
  {
    "ordinal": 7,
    "name": "p_drill_field",
    "mysqlDataType": "varchar",
    "sourceType": "DRILL",
    "sourceKey": "field"
  },
  {
    "ordinal": 8,
    "name": "p_drill_value",
    "mysqlDataType": "json",
    "sourceType": "DRILL",
    "sourceKey": "value"
  }
]
```

### 5.1 空值规则

- 根层：`p_drill_field=''`、`p_drill_value=NULL`。
- 非必填且未填写的文本、日期、数字和单选绑定 SQL NULL，而不是空字符串。
- 多选未选择时绑定空 JSON 数组 `[]`；这与 SQL NULL 含义不同。
- 日期范围任一边缺失时，分别绑定 NULL；是否允许由控件必填规则决定。
- `CONSTANT` 按目标参数类型转换；`NULL` 始终使用 `setNull` 和对应 JDBC 类型。

### 5.2 类型转换

| MySQL 类型 | Java 绑定 | JSON 输出 |
|---|---|---|
| char/varchar/text | String | string |
| tinyint/smallint/int/bigint | Integer/Long/BigInteger | number；超出 JS 安全整数时 string |
| decimal/numeric | BigDecimal | number；超精度时按字段配置输出 string |
| float/double | Double | number，NaN/Infinity 禁止 |
| date | LocalDate | `YYYY-MM-DD` |
| datetime/timestamp | LocalDateTime/OffsetDateTime | ISO 8601 string |
| boolean/bit(1) | Boolean | boolean |
| json | String/MySQL JSON | object、array 或 scalar |

转换失败在调用前返回 `BI_REQUEST_INVALID`，不进入数据库。

## 6. 钻取载荷

### 6.1 返回方式

源层结果集返回隐藏字段：

```sql
JSON_OBJECT(
    'saleDate', DATE_FORMAT(s.sale_date, '%Y-%m-%d'),
    'storeId', s.store_id
) AS F_drill_payload
```

源路由配置：

```json
{
  "sourceRouteCode": "ROOT",
  "triggerField": "F_store_name",
  "payloadField": "F_drill_payload",
  "targetRouteCode": "STORE_DETAIL",
  "routeValue": "store_detail"
}
```

点击后服务端绑定：

```text
p_drill_field = 'store_detail'
p_drill_value = {"saleDate":"2026-08-22","storeId":"S001"}
```

### 6.2 读取方式

```sql
SET v_sale_date = STR_TO_DATE(
    JSON_UNQUOTE(JSON_EXTRACT(p_drill_value, '$.saleDate')),
    '%Y-%m-%d'
);
SET v_store_id = JSON_UNQUOTE(JSON_EXTRACT(p_drill_value, '$.storeId'));
```

载荷必须包含下一层所需的全部祖先上下文。三级下钻时，第二层生成的新载荷应继续包含日期、门店等上级键，并加入新的业务键：

```sql
JSON_OBJECT(
    'saleDate', DATE_FORMAT(v_sale_date, '%Y-%m-%d'),
    'storeId', v_store_id,
    'customerId', d.customer_id
) AS F_customer_payload
```

不要把 JSON 再拼接成以 `#` 分隔的字符串。

## 7. 结果集契约

### 7.1 字段名称

- 物理字段名建议使用稳定别名，例如 `F_sale_date`、`F_amount`。
- 字段匹配默认大小写敏感；存储过程别名必须与路由字段配置完全一致。
- 每个分支可以返回完全不同的列，但必须满足目标路由的全部引用字段。
- 未配置的额外列不会返回前端。

### 7.2 字段分类

| 分类 | 是否显示 | 示例 |
|---|---:|---|
| 维度 | 是 | 日期、门店、客户 |
| 度量/值 | 是 | 销售额、订单数 |
| 载荷 | 否 | `F_drill_payload` |
| 样式 | 否 | `F_amount_style` |

### 7.3 样式字段

```sql
CASE
    WHEN SUM(amount) >= 100000 THEN '#FF0000,bold'
    ELSE '#333333,normal'
END AS F_amount_style
```

允许格式只有：

```text
#[0-9A-Fa-f]{6},(bold|normal)
```

解析后前端只设置受控的 `color` 和 `fontWeight`。不得返回 `style="..."`、类名、HTML 或 JavaScript。

## 8. 完整示例存储过程

以下示例用一个存储过程服务上方指标卡、趋势图和下方可异构下钻表格。三个对象分别调用过程，可以返回完全不相关的数据。表名是示例，实际实现需替换为业务表。

```sql
DELIMITER $$

CREATE PROCEDURE sp_bi_sales (
    IN p_user_id       VARCHAR(64),
    IN p_region_key    VARCHAR(16),
    IN p_component_key VARCHAR(64),
    IN p_date_start    DATE,
    IN p_date_end      DATE,
    IN p_store_ids     JSON,
    IN p_drill_field   VARCHAR(64),
    IN p_drill_value   JSON
)
SQL SECURITY INVOKER
READS SQL DATA
BEGIN
    DECLARE v_sale_date DATE DEFAULT NULL;
    DECLARE v_store_id VARCHAR(64) DEFAULT NULL;
    DECLARE v_customer_id VARCHAR(64) DEFAULT NULL;

    IF p_drill_value IS NOT NULL THEN
        SET v_sale_date = STR_TO_DATE(
            JSON_UNQUOTE(JSON_EXTRACT(p_drill_value, '$.saleDate')),
            '%Y-%m-%d'
        );
        SET v_store_id = JSON_UNQUOTE(JSON_EXTRACT(p_drill_value, '$.storeId'));
        SET v_customer_id = JSON_UNQUOTE(JSON_EXTRACT(p_drill_value, '$.customerId'));
    END IF;

    IF p_region_key = 'COMPONENT'
       AND p_component_key = 'sales_kpi' THEN
        SELECT
            COALESCE(SUM(f.amount), 0) AS F_amount,
            CASE
                WHEN COALESCE(SUM(f.amount), 0) >= 1000000 THEN '#FF0000,bold'
                ELSE '#333333,normal'
            END AS F_amount_style
        FROM fact_sales f
        JOIN user_store_acl a
          ON a.store_id = f.store_id
         AND a.user_id = p_user_id
        WHERE (p_date_start IS NULL OR f.sale_date >= p_date_start)
          AND (p_date_end IS NULL OR f.sale_date <= p_date_end)
          AND (
              JSON_LENGTH(COALESCE(p_store_ids, JSON_ARRAY())) = 0
              OR f.store_id IN (
                  SELECT j.store_id
                  FROM JSON_TABLE(
                      p_store_ids,
                      '$[*]' COLUMNS(store_id VARCHAR(64) PATH '$')
                  ) j
              )
          );

    ELSEIF p_region_key = 'COMPONENT'
       AND p_component_key = 'sales_trend' THEN
        SELECT
            f.sale_date AS F_sale_date,
            SUM(f.amount) AS F_amount
        FROM fact_sales f
        JOIN user_store_acl a
          ON a.store_id = f.store_id
         AND a.user_id = p_user_id
        WHERE (p_date_start IS NULL OR f.sale_date >= p_date_start)
          AND (p_date_end IS NULL OR f.sale_date <= p_date_end)
        GROUP BY f.sale_date
        ORDER BY f.sale_date;

    ELSEIF p_region_key = 'TABLE'
       AND p_component_key = 'main_table'
       AND COALESCE(p_drill_field, '') = '' THEN
        SELECT
            f.sale_date AS F_sale_date,
            s.store_name AS F_store_name,
            SUM(f.amount) AS F_amount,
            CASE
                WHEN SUM(f.amount) >= 100000 THEN '#FF0000,bold'
                ELSE '#333333,normal'
            END AS F_amount_style,
            JSON_OBJECT(
                'saleDate', DATE_FORMAT(f.sale_date, '%Y-%m-%d'),
                'storeId', f.store_id
            ) AS F_drill_payload
        FROM fact_sales f
        JOIN dim_store s ON s.store_id = f.store_id
        JOIN user_store_acl a
          ON a.store_id = f.store_id
         AND a.user_id = p_user_id
        WHERE (p_date_start IS NULL OR f.sale_date >= p_date_start)
          AND (p_date_end IS NULL OR f.sale_date <= p_date_end)
        GROUP BY f.sale_date, f.store_id, s.store_name
        ORDER BY f.sale_date, f.store_id;

    ELSEIF p_region_key = 'TABLE'
       AND p_component_key = 'main_table'
       AND p_drill_field = 'store_detail' THEN
        SELECT
            f.sale_date AS F_sale_date,
            f.customer_id AS F_customer_id,
            c.customer_name AS F_customer_name,
            e.employee_name AS F_salesman,
            SUM(f.amount) AS F_amount,
            JSON_OBJECT(
                'saleDate', DATE_FORMAT(v_sale_date, '%Y-%m-%d'),
                'storeId', v_store_id,
                'customerId', f.customer_id
            ) AS F_customer_payload
        FROM fact_sales f
        JOIN dim_customer c ON c.customer_id = f.customer_id
        JOIN dim_employee e ON e.employee_id = f.salesman_id
        JOIN user_store_acl a
          ON a.store_id = f.store_id
         AND a.user_id = p_user_id
        WHERE f.sale_date = v_sale_date
          AND f.store_id = v_store_id
        GROUP BY f.sale_date, f.customer_id, c.customer_name, e.employee_name;

    ELSEIF p_region_key = 'TABLE'
       AND p_component_key = 'main_table'
       AND p_drill_field = 'customer_orders' THEN
        SELECT
            f.order_no AS F_order_no,
            f.sale_time AS F_sale_time,
            p.product_name AS F_product_name,
            f.quantity AS F_quantity,
            f.amount AS F_amount
        FROM fact_sales f
        JOIN dim_product p ON p.product_id = f.product_id
        JOIN user_store_acl a
          ON a.store_id = f.store_id
         AND a.user_id = p_user_id
        WHERE f.sale_date = v_sale_date
          AND f.store_id = v_store_id
          AND f.customer_id = v_customer_id
        ORDER BY f.sale_time, f.order_no;

    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Unsupported BI region, component or drill route';
    END IF;
END$$

DELIMITER ;
```

每条执行路径只能产生一个 SELECT 结果集。调试 SELECT、临时结果集和多余状态查询必须在部署前移除。

## 9. SQL 控件选项规范

允许示例：

```sql
SELECT store_id AS value, store_name AS label
FROM dim_store
WHERE enabled = 1
  AND EXISTS (
      SELECT 1
      FROM user_store_acl a
      WHERE a.store_id = dim_store.store_id
        AND a.user_id = :currentUserId
  )
ORDER BY store_name
```

第一版 SQL 选项不引用其他控件。实现必须：

1. 解析并确认只有一条 SELECT/CTE 查询。
2. 禁止分号后的第二语句、注释逃逸、`INTO OUTFILE`、`FOR UPDATE`、DML、DDL 和过程调用。
3. 强制最大 1,000 行和 10 秒超时。
4. 只读取 `value`、`label` 两列，缺失或重名时报错。
5. 只允许命名参数 `:currentUserId`，由服务端从登录上下文绑定；禁止字符串替换和客户端提供该值。

仅依赖关键词正则不足以构成安全校验；应使用 SQL 解析器和只读数据库权限双重保护。

## 10. 开发者检查清单

- 参数模式全部为 IN，签名已同步且映射完整。
- `p_user_id` 在每个业务查询分支中生效，没有可绕过的分支。
- `p_region_key`、`p_component_key` 和表格 `p_drill_field` 的所有配置值均有明确分支。
- 每条执行路径只返回一个结果集。
- 所有目标路由所需字段、载荷字段和样式字段都存在且别名完全匹配。
- JSON 载荷包含下一层需要的全部祖先键，不包含敏感明文或无关大对象。
- 多选用 `JSON_TABLE` 或等价 JSON 处理，不使用字符串分隔符。
- 查询具备必要索引，并在最大日期范围和最大用户权限范围下完成执行计划检查。
