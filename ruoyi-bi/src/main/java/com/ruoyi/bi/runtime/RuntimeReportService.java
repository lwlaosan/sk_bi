package com.ruoyi.bi.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.bi.api.BiException;
import com.ruoyi.bi.config.BiProperties;
import com.ruoyi.bi.datasource.DatasourceService;
import com.ruoyi.bi.report.ReportAccess;
import com.ruoyi.bi.report.ReportRepository;
import com.ruoyi.bi.report.ReportRuntimeCache;
import com.ruoyi.bi.report.ReportStatus;
import com.ruoyi.bi.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

@Service
public class RuntimeReportService {
    private static final Logger log = LoggerFactory.getLogger(RuntimeReportService.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_$]{1,128}");
    private static final Pattern UNSAFE_SQL = Pattern.compile("(?i)\\b(insert|update|delete|drop|alter|create|grant|revoke|call|outfile|dumpfile|load_file|sleep|benchmark|for\\s+update|lock\\s+in\\s+share)\\b");
    private final ReportRepository reports; private final ReportAccess access; private final ReportRuntimeCache cache;
    private final DatasourceService datasources; private final ProcedureParameterBinder binder;
    private final QueryConcurrency concurrency; private final CurrentUser user; private final ObjectMapper mapper;
    private final BiProperties properties;
    private final RuntimeAuditRepository audit;

    public RuntimeReportService(ReportRepository reports, ReportAccess access, ReportRuntimeCache cache,
                                DatasourceService datasources, ProcedureParameterBinder binder,
                                QueryConcurrency concurrency, CurrentUser user, ObjectMapper mapper, BiProperties properties,
                                RuntimeAuditRepository audit) {
        this.reports=reports; this.access=access; this.cache=cache; this.datasources=datasources; this.binder=binder;
        this.concurrency=concurrency; this.user=user; this.mapper=mapper; this.properties=properties; this.audit=audit;
    }

    public ObjectNode configuration(String uuid) {
        Loaded loaded = load(uuid); ObjectNode config = loaded.snapshot(); ObjectNode result = mapper.createObjectNode();
        result.put("uuid", uuid); result.put("name", loaded.head().name()); result.put("configVersion", loaded.head().version());
        result.put("maxRows", loaded.head().maxRows());
        ArrayNode controls = result.putArray("controls");
        config.path("controls").forEach(node -> {
            ObjectNode item = controls.addObject(); item.put("key", node.path("controlKey").asText());
            item.put("label", node.path("label").asText()); item.put("type", node.path("controlType").asText());
            item.put("required", node.path("required").asBoolean()); item.set("defaultValue", node.path("defaultValue"));
            item.put("optionSource", node.path("optionSource").asText()); item.set("targetComponentKeys", node.path("targetComponentKeys"));
        });
        ArrayNode components = result.putArray("components");
        config.path("components").forEach(node -> {
            ObjectNode item = components.addObject(); item.put("key", node.path("componentKey").asText());
            item.put("name", node.path("componentName").asText()); item.put("regionType", node.path("regionType").asText());
            item.put("titleVisible", node.path("titleVisible").asBoolean(true)); item.set("layout", node.path("layout"));
            JsonNode root = StreamSupport.stream(node.path("routes").spliterator(), false)
                .filter(route -> "ROOT".equals(route.path("routeCode").asText())).findFirst().orElse(mapper.createObjectNode());
            ObjectNode runtimeRoute = item.putObject("rootRoute"); runtimeRoute.put("code", "ROOT");
            runtimeRoute.put("name", root.path("routeName").asText()); runtimeRoute.put("viewType", root.path("viewType").asText());
            runtimeRoute.set("chartConfig", root.path("chartConfig"));
        });
        return result;
    }

    public RuntimeDtos.OptionResult options(String uuid, String controlKey) {
        Loaded loaded = load(uuid);
        RuntimeDtos.OptionResult cached=cache.getOption(uuid,controlKey,user.id(),RuntimeDtos.OptionResult.class);if(cached!=null)return cached;
        JsonNode control = find(loaded.snapshot().path("controls"), "controlKey", controlKey, "BI_REQUEST_INVALID", "控件不存在");
        String source = control.path("optionSource").asText();
        if ("STATIC".equals(source)) {
            List<RuntimeDtos.OptionItem> items = StreamSupport.stream(control.path("options").spliterator(), false)
                .filter(node -> node.path("enabled").asBoolean(true))
                .map(node -> new RuntimeDtos.OptionItem(node.path("value").asText(), node.path("label").asText())).toList();
            RuntimeDtos.OptionResult result=new RuntimeDtos.OptionResult(items,false);cache.putOption(uuid,controlKey,user.id(),result);return result;
        }
        if (!"SQL".equals(source)) return new RuntimeDtos.OptionResult(List.of(), false);
        long datasourceId = control.path("optionDatasourceId").asLong(); String sql = control.path("optionSql").asText().trim();
        validateOptionSql(sql);
        boolean bindUser = sql.contains(":currentUserId");
        String jdbcSql = sql.replace(":currentUserId", "?");
        try (QueryConcurrency.Permit ignored = concurrency.acquire(user.id(), datasourceId);
             Connection connection = datasources.openForRuntime(datasourceId);
             var statement = connection.prepareStatement(jdbcSql)) {
            statement.setQueryTimeout(properties.option().timeoutSeconds());
            statement.setMaxRows(properties.option().maxRows() + 1); if (bindUser) statement.setLong(1, user.id());
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                if (md.getColumnCount() != 2 || !"value".equalsIgnoreCase(md.getColumnLabel(1)) || !"label".equalsIgnoreCase(md.getColumnLabel(2)))
                    throw new BiException(HttpStatus.UNPROCESSABLE_ENTITY, "BI_CONTROL_OPTION_SQL_INVALID", "SQL 选项必须仅返回 value、label 两列");
                List<RuntimeDtos.OptionItem> items = new ArrayList<>();
                while (rs.next() && items.size() <= properties.option().maxRows()) items.add(new RuntimeDtos.OptionItem(rs.getString(1), rs.getString(2)));
                boolean truncated = items.size() > properties.option().maxRows(); if (truncated) items.remove(items.size()-1);
                RuntimeDtos.OptionResult result=new RuntimeDtos.OptionResult(List.copyOf(items), truncated);cache.putOption(uuid,controlKey,user.id(),result);return result;
            }
        } catch (BiException ex) { throw ex; }
        catch (SQLException ex) { throw queryFailure(ex); }
    }

    public RuntimeDtos.QueryResult query(String uuid, String componentKey, RuntimeDtos.QueryRequest request, String traceId) {
        long started = System.nanoTime(); Loaded loaded = load(uuid);
        if (request.configVersion() != loaded.head().version()) throw new BiException(HttpStatus.CONFLICT, "BI_RUNTIME_CONFIG_STALE", "运行配置已更新");
        JsonNode component = find(loaded.snapshot().path("components"), "componentKey", componentKey,
            "BI_COMPONENT_NOT_FOUND", "组件不存在");
        String region = component.path("regionType").asText(); JsonNode route = route(component, request.drill(), region);
        Map<String,JsonNode> controls = validateControls(loaded.snapshot(), componentKey, request.controls());
        long datasourceId = component.path("datasourceIdOverride").asLong(loaded.snapshot().path("baseInfo").path("defaultDatasourceId").asLong());
        String procedure = component.path("procedureNameOverride").asText(loaded.snapshot().path("baseInfo").path("defaultProcedureName").asText());
        String signature = component.path("signatureHashOverride").asText(loaded.snapshot().path("baseInfo").path("defaultSignatureHash").asText());
        if (!IDENTIFIER.matcher(procedure).matches()) throw invalid("存储过程名称不合法");
        JsonNode mappings = mappings(loaded.snapshot(), componentKey, component.hasNonNull("procedureNameOverride"));
        var metadata = datasources.parametersForRuntime(datasourceId, procedure);
        if (!metadata.supported() || !signature.equals(metadata.signatureHash()) || mappings.size() != metadata.parameters().size())
            throw new BiException(HttpStatus.CONFLICT, "BI_SP_SIGNATURE_CHANGED", "存储过程签名已变化");
        String drillField = request.drill() == null || request.drill().isNull() ? "" : request.drill().path("field").asText();
        JsonNode drillValue = request.drill() == null || request.drill().isNull() ? mapper.nullNode() : request.drill().path("value");
        var params = binder.resolve(mappings, new ProcedureParameterBinder.Context(user.id(), region, componentKey, controls, drillField, drillValue));
        int limit = Math.min(properties.query().hardMaxRows(), loaded.head().maxRows());
        try (QueryConcurrency.Permit ignored = concurrency.acquire(user.id(), datasourceId);
             Connection connection = datasources.openForRuntime(datasourceId);
             CallableStatement statement = connection.prepareCall(callSql(procedure, params.size()))) {
            statement.setQueryTimeout(Math.min(properties.query().maxTimeoutSeconds(), loaded.head().timeoutSeconds()));
            statement.setMaxRows(limit + 1); binder.bind(statement, params);
            boolean resultSet = statement.execute();
            while (!resultSet && statement.getUpdateCount() != -1) resultSet = statement.getMoreResults();
            if (!resultSet) throw new BiException(HttpStatus.INTERNAL_SERVER_ERROR, "BI_QUERY_FAILED", "存储过程未返回结果集");
            List<Map<String,Object>> rows;
            try (ResultSet rs = statement.getResultSet()) { rows = readRows(rs, route.path("fields"), limit); }
            boolean truncated = rows.size() > limit; if (truncated) rows.remove(rows.size()-1);
            while (statement.getMoreResults() || statement.getUpdateCount() != -1) {
                if (statement.getResultSet() != null) throw new BiException(HttpStatus.BAD_REQUEST, "BI_SP_PARAMETER_UNSUPPORTED", "不支持多个结果集");
            }
            long elapsed=(System.nanoTime()-started)/1_000_000;
            audit.success(loaded.head().id(),uuid,componentKey,user.id(),datasourceId,procedure,request.requestId(),traceId,elapsed,rows.size(),truncated);
            return new RuntimeDtos.QueryResult(request.requestId(), componentKey, region, publicRoute(route),
                runtimeFields(route), List.copyOf(rows), rows.size(), truncated, limit,
                elapsed, traceId);
        } catch (BiException ex) { throw ex; }
        catch (SQLException ex) {
            log.warn("报表查询失败 uuid={} component={} sqlState={}: {}", uuid, componentKey, ex.getSQLState(), ex.getMessage());
            throw queryFailure(ex);
        }
    }

    public List<RuntimeDtos.QueryResult> queryAll(String uuid,RuntimeDtos.QueryRequest request,String traceId){
        Loaded loaded=load(uuid);List<RuntimeDtos.QueryResult> results=new ArrayList<>();
        for(JsonNode component:loaded.snapshot().path("components")){
            RuntimeDtos.QueryRequest root=new RuntimeDtos.QueryRequest(request.configVersion(),request.controls(),null,request.requestId()+"-"+component.path("componentKey").asText());
            results.add(query(uuid,component.path("componentKey").asText(),root,traceId));
        }return List.copyOf(results);
    }

    private Loaded load(String uuid) {
        try { UUID.fromString(uuid); } catch (Exception ex) { throw new BiException(HttpStatus.NOT_FOUND, "BI_REPORT_NOT_FOUND", "报表不存在"); }
        ReportRepository.RuntimeHead head = reports.runtimeHead(uuid); access.requireReadable(head.id(), reports);
        if (head.status() != ReportStatus.ENABLED) throw new BiException(HttpStatus.NOT_FOUND, "BI_REPORT_NOT_FOUND", "报表不存在");
        return new Loaded(head, cache.get(reports, head));
    }

    private JsonNode route(JsonNode component, JsonNode drill, String region) {
        if (drill != null && !drill.isNull() && !"TABLE".equals(region))
            throw new BiException(HttpStatus.UNPROCESSABLE_ENTITY, "BI_DRILL_NOT_ALLOWED", "上方组件不允许钻取");
        String code = drill == null || drill.isNull() ? "ROOT" : drill.path("routeCode").asText();
        JsonNode route = find(component.path("routes"), "routeCode", code, "BI_DRILL_NOT_ALLOWED", "钻取路由不存在");
        if (!"ROOT".equals(code)) {
            String value = drill.path("field").asText();
            boolean valid = StreamSupport.stream(component.path("routes").spliterator(), false)
                .flatMap(r -> StreamSupport.stream(r.path("drillEdges").spliterator(), false))
                .anyMatch(edge -> code.equals(edge.path("targetRouteCode").asText()) && value.equals(edge.path("routeValue").asText()));
            if (!valid || !drill.path("value").isObject()) throw new BiException(HttpStatus.UNPROCESSABLE_ENTITY, "BI_DRILL_NOT_ALLOWED", "钻取边无效");
        }
        return route;
    }

    private Map<String,JsonNode> validateControls(JsonNode config, String componentKey, Map<String,JsonNode> submitted) {
        Map<String,JsonNode> result = new LinkedHashMap<>(); Map<String,JsonNode> safe = submitted == null ? Map.of() : submitted;
        for (JsonNode control : config.path("controls")) {
            boolean target = StreamSupport.stream(control.path("targetComponentKeys").spliterator(), false).anyMatch(n -> componentKey.equals(n.asText()));
            if (!target) continue; JsonNode value = safe.get(control.path("controlKey").asText());
            if (control.path("required").asBoolean() && (value == null || value.isNull() || value.isEmpty())) throw invalid("缺少必填控件: " + control.path("label").asText());
            if (value != null) result.put(control.path("controlKey").asText(), value);
        }
        return result;
    }

    private JsonNode mappings(JsonNode config, String componentKey, boolean override) {
        ArrayNode selected = mapper.createArrayNode();
        config.path("parameterMappings").forEach(mapping -> {
            String key = mapping.path("componentKey").asText(); if (override ? componentKey.equals(key) : key.isBlank()) selected.add(mapping);
        }); return selected;
    }

    private List<Map<String,Object>> readRows(ResultSet rs, JsonNode fields, int limit) throws SQLException {
        ResultSetMetaData md = rs.getMetaData(); Map<String,Integer> columns = new LinkedHashMap<>();
        for (int i=1;i<=md.getColumnCount();i++) columns.put(md.getColumnLabel(i), i);
        for (JsonNode field : fields) if (!columns.containsKey(field.path("physicalName").asText()))
            throw new BiException(HttpStatus.UNPROCESSABLE_ENTITY, "BI_RESULT_FIELD_MISSING", "结果缺少配置字段: " + field.path("physicalName").asText());
        List<Map<String,Object>> rows = new ArrayList<>();
        while (rs.next() && rows.size() <= limit) {
            Map<String,Object> row = new LinkedHashMap<>();
            for (JsonNode field : fields) {
                String name=field.path("physicalName").asText(); Object value=rs.getObject(columns.get(name));
                row.put(name, normalize(value, field.path("dataType").asText()));
            } rows.add(row);
        } return rows;
    }

    private Object normalize(Object value, String type) {
        if (value == null) return null;
        try {
            if ("JSON".equals(type) && value instanceof String text) return mapper.readTree(text);
            if (value instanceof BigInteger number && number.abs().compareTo(BigInteger.valueOf(9_007_199_254_740_991L)) > 0) return number.toString();
            if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
            if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toString();
            if (value instanceof byte[] bytes) return java.util.Base64.getEncoder().encodeToString(bytes);
            return value;
        } catch (Exception ex) { throw invalid("结果字段序列化失败"); }
    }

    private ObjectNode publicRoute(JsonNode route) {
        ObjectNode out=mapper.createObjectNode(); out.put("code",route.path("routeCode").asText());
        out.put("name",route.path("routeName").asText()); out.put("viewType",route.path("viewType").asText()); out.set("chartConfig",route.path("chartConfig")); return out;
    }
    private ArrayNode runtimeFields(JsonNode route) {
        ArrayNode fields=mapper.createArrayNode();
        route.path("fields").forEach(field->{ObjectNode copy=(ObjectNode)field.deepCopy();
            StreamSupport.stream(route.path("drillEdges").spliterator(),false)
                .filter(edge->field.path("physicalName").asText().equals(edge.path("triggerField").asText())).findFirst()
                .ifPresent(edge->{ObjectNode drill=copy.putObject("drill");drill.put("targetRouteCode",edge.path("targetRouteCode").asText());drill.put("routeValue",edge.path("routeValue").asText());drill.put("payloadField",edge.path("payloadField").asText());});
            fields.add(copy);}); return fields;
    }
    private void validateOptionSql(String sql) {
        String lower=sql.toLowerCase();
        if (!(lower.startsWith("select ")||lower.startsWith("with ")) || sql.contains(";") || sql.contains("--") || sql.contains("/*") || UNSAFE_SQL.matcher(sql).find())
            throw new BiException(HttpStatus.UNPROCESSABLE_ENTITY,"BI_CONTROL_OPTION_SQL_INVALID","SQL 选项不符合只读规则");
        String stripped=sql.replace(":currentUserId",""); if (stripped.contains(":")) throw new BiException(HttpStatus.UNPROCESSABLE_ENTITY,"BI_CONTROL_OPTION_SQL_INVALID","只允许 :currentUserId 参数");
        if (sql.indexOf(":currentUserId") != sql.lastIndexOf(":currentUserId")) throw new BiException(HttpStatus.UNPROCESSABLE_ENTITY,"BI_CONTROL_OPTION_SQL_INVALID","系统参数只能出现一次");
        try {
            var parsed=net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql.replace(":currentUserId","?"));
            if (!(parsed instanceof net.sf.jsqlparser.statement.select.Select)) throw new IllegalArgumentException();
        } catch(Exception ex) { throw new BiException(HttpStatus.UNPROCESSABLE_ENTITY,"BI_CONTROL_OPTION_SQL_INVALID","SQL 选项语法不是单条只读 SELECT/CTE"); }
    }
    private static String callSql(String procedure,int count){return "{call `"+procedure+"`("+String.join(",",java.util.Collections.nCopies(count,"?"))+")}";}
    private static JsonNode find(JsonNode array,String field,String value,String code,String message){return StreamSupport.stream(array.spliterator(),false).filter(n->value.equals(n.path(field).asText())).findFirst().orElseThrow(()->new BiException(HttpStatus.NOT_FOUND,code,message));}
    private static BiException invalid(String message){return new BiException(HttpStatus.BAD_REQUEST,"BI_REQUEST_INVALID",message);}
    private static BiException queryFailure(SQLException ex) {
        String state = ex.getSQLState() == null ? "" : ex.getSQLState();
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String lower = message.toLowerCase();
        if (state.startsWith("08")) return new BiException(HttpStatus.SERVICE_UNAVAILABLE, "BI_DATASOURCE_UNAVAILABLE", "数据源不可用");
        if (lower.contains("timeout") || lower.contains("timed out")) return new BiException(HttpStatus.GATEWAY_TIMEOUT, "BI_QUERY_TIMEOUT", "查询超时");
        if (lower.contains("read-only") || lower.contains("read only"))
            return new BiException(HttpStatus.SERVICE_UNAVAILABLE, "BI_DATASOURCE_UNAVAILABLE", "当前连接被设为只读，无法调用存储过程");
        if ("45000".equals(state) && !message.isBlank())
            return new BiException(HttpStatus.BAD_REQUEST, "BI_QUERY_FAILED", message);
        return new BiException(HttpStatus.INTERNAL_SERVER_ERROR, "BI_QUERY_FAILED",
            message.isBlank() ? "查询执行失败" : "查询执行失败：" + message);
    }
    private record Loaded(ReportRepository.RuntimeHead head,ObjectNode snapshot){}
}
