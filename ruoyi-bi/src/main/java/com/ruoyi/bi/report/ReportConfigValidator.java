package com.ruoyi.bi.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ReportConfigValidator {
    public ReportDtos.ValidationResult validate(JsonNode config) {
        List<ReportDtos.ValidationIssue> errors = new ArrayList<>();
        JsonNode base = config.path("baseInfo");
        requiredText(base, "reportName", 150, errors);
        requiredText(base, "defaultDatasourceId", 30, errors);
        requiredText(base, "defaultProcedureName", 128, errors);
        enumValue(base, "status", Set.of("ENABLED", "DISABLED"), errors);
        range(base, "maxRows", 1, 200_000, errors);
        range(base, "timeoutSeconds", 1, 600, errors);
        if (!config.path("acl").path("roleIds").isArray() || !config.path("acl").path("userIds").isArray()) {
            errors.add(issue("acl", "ACL_INVALID", "角色和用户 ACL 必须是数组"));
        }
        if (!config.path("controls").isArray() || !config.path("parameterMappings").isArray()) {
            errors.add(issue("controls", "COLLECTION_INVALID", "控件和参数映射必须是数组"));
        }
        validateComponents(config.path("components"), errors);
        validateControls(config.path("controls"), config.path("components"), errors);
        validateMappings(config.path("parameterMappings"), config.path("controls"), config.path("components"), errors);
        validateInsight(config.path("insight"), errors);
        return new ReportDtos.ValidationResult(errors.isEmpty(), List.copyOf(errors), List.of());
    }

    private void validateInsight(JsonNode insight, List<ReportDtos.ValidationIssue> errors) {
        if (insight.isMissingNode() || insight.isNull()) return;
        if (!insight.isObject()) { errors.add(issue("insight", "INSIGHT_INVALID", "洞察配置必须是对象")); return; }
        if (!insight.path("enabled").asBoolean(false)) return;
        if (!Set.of("QWEN", "DEEPSEEK").contains(insight.path("provider").asText()))
            errors.add(issue("insight.provider", "INSIGHT_PROVIDER_INVALID", "洞察模型供应商不合法"));
        String model=insight.path("model").asText(), prompt=insight.path("prompt").asText(), title=insight.path("title").asText();
        if (model.isBlank() || model.length()>100) errors.add(issue("insight.model", "INSIGHT_MODEL_INVALID", "模型名称不能为空且不能超过100字"));
        if (prompt.isBlank() || prompt.length()>8000) errors.add(issue("insight.prompt", "INSIGHT_PROMPT_INVALID", "开发者提示词不能为空且不能超过8000字"));
        if (title.isBlank() || title.length()>50) errors.add(issue("insight.title", "INSIGHT_TITLE_INVALID", "洞察标题不能为空且不能超过50字"));
        if (!Set.of("HEADER", "FLOAT_RIGHT", "BOTTOM").contains(insight.path("position").asText()))
            errors.add(issue("insight.position", "INSIGHT_POSITION_INVALID", "洞察入口位置不合法"));
        int rows=insight.path("maxRowsPerComponent").asInt(-1), tokens=insight.path("maxTokens").asInt(-1);
        double temperature=insight.path("temperature").asDouble(-1);
        if(rows<1||rows>200) errors.add(issue("insight.maxRowsPerComponent","INSIGHT_LIMIT_INVALID","每组件数据行数必须在1到200之间"));
        if(tokens<128||tokens>8192) errors.add(issue("insight.maxTokens","INSIGHT_LIMIT_INVALID","最大输出 Token 必须在128到8192之间"));
        if(temperature<0||temperature>2) errors.add(issue("insight.temperature","INSIGHT_TEMPERATURE_INVALID","温度必须在0到2之间"));
    }

    private void validateControls(JsonNode controls, JsonNode components, List<ReportDtos.ValidationIssue> errors) {
        if (!controls.isArray()) return;
        Set<String> keys = new HashSet<>(); Set<String> componentKeys = new HashSet<>();
        components.forEach(node -> componentKeys.add(node.path("componentKey").asText()));
        Set<String> types = Set.of("TEXT", "SINGLE_SELECT", "MULTI_SELECT", "DATE", "DATE_RANGE", "NUMBER", "NUMBER_RANGE");
        Set<String> sources = Set.of("NONE", "STATIC", "SQL");
        for (int i = 0; i < controls.size(); i++) {
            JsonNode control = controls.get(i); String path = "controls[" + i + "]";
            String key = control.path("controlKey").asText();
            if (key.isBlank() || !key.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || !keys.add(key)) errors.add(issue(path + ".controlKey", "CONTROL_KEY_INVALID", "控件键格式不合法或重复"));
            if (control.path("label").asText().isBlank()) errors.add(issue(path + ".label", "FIELD_INVALID", "控件标签不能为空"));
            if (!types.contains(control.path("controlType").asText())) errors.add(issue(path + ".controlType", "CONTROL_TYPE_INVALID", "控件类型不合法"));
            String source = control.path("optionSource").asText("NONE");
            if (!sources.contains(source)) errors.add(issue(path + ".optionSource", "OPTION_SOURCE_INVALID", "选项来源不合法"));
            if ("STATIC".equals(source)) validateStaticOptions(control.path("options"), path, errors);
            if ("SQL".equals(source)) {
                if (control.path("optionDatasourceId").asLong(0) <= 0) errors.add(issue(path + ".optionDatasourceId", "DATASOURCE_REQUIRED", "SQL 选项必须指定数据源"));
                String optionSql = control.path("optionSql").asText();
                String sqlIssue = optionSqlIssue(optionSql);
                if (sqlIssue != null) errors.add(issue(path + ".optionSql", "OPTION_SQL_UNSAFE", sqlIssue));
            }
            for (JsonNode target : control.path("targetComponentKeys")) if (!componentKeys.contains(target.asText())) errors.add(issue(path + ".targetComponentKeys", "COMPONENT_NOT_FOUND", "控件目标组件不存在"));
        }
    }

    private void validateStaticOptions(JsonNode options, String path, List<ReportDtos.ValidationIssue> errors) {
        if (!options.isArray()) {
            errors.add(issue(path + ".options", "OPTIONS_INVALID", "静态选项必须是数组"));
            return;
        }
        Set<String> values = new HashSet<>();
        for (int i = 0; i < options.size(); i++) {
            JsonNode option = options.get(i);
            String optionPath = path + ".options[" + i + "]";
            String value = option.path("value").asText();
            if (value.isBlank() || !values.add(value)) errors.add(issue(optionPath + ".value", "OPTION_VALUE_INVALID", "静态选项值不能为空且必须唯一"));
            if (option.path("label").asText().isBlank()) errors.add(issue(optionPath + ".label", "OPTION_LABEL_REQUIRED", "静态选项标签不能为空"));
        }
    }

    private void validateMappings(JsonNode mappings, JsonNode controls, JsonNode components, List<ReportDtos.ValidationIssue> errors) {
        if (!mappings.isArray()) return;
        Set<String> controlKeys = new HashSet<>(); controls.forEach(node -> controlKeys.add(node.path("controlKey").asText()));
        Set<String> componentKeys = new HashSet<>(); components.forEach(node -> componentKeys.add(node.path("componentKey").asText()));
        Set<String> sourceTypes = Set.of("SYSTEM", "REGION", "COMPONENT", "CONTROL", "DRILL", "CONSTANT", "NULL");
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < mappings.size(); i++) {
            JsonNode mapping = mappings.get(i); String path = "parameterMappings[" + i + "]";
            String componentKey = mapping.path("componentKey").asText("");
            if (!componentKey.isBlank() && !componentKeys.contains(componentKey)) errors.add(issue(path + ".componentKey", "COMPONENT_NOT_FOUND", "参数映射组件不存在"));
            String identity = componentKey + "|" + mapping.path("procedureName").asText() + "|" + mapping.path("parameterOrdinal").asInt();
            if (!unique.add(identity)) errors.add(issue(path, "PARAMETER_DUPLICATE", "参数映射作用域和顺序重复"));
            if (!"IN".equalsIgnoreCase(mapping.path("parameterMode").asText())) errors.add(issue(path + ".parameterMode", "PARAMETER_MODE_INVALID", "第一版仅支持 IN 参数"));
            String sourceType = mapping.path("sourceType").asText();
            if (!sourceTypes.contains(sourceType)) errors.add(issue(path + ".sourceType", "PARAMETER_SOURCE_INVALID", "参数来源不合法"));
            String parameterName = mapping.path("parameterName").asText(); String sourceKey = mapping.path("sourceKey").asText();
            if ("p_user_id".equalsIgnoreCase(parameterName) && !("SYSTEM".equals(sourceType) && "user_id".equals(sourceKey))) errors.add(issue(path, "SYSTEM_PARAMETER_REQUIRED", "p_user_id 必须映射为 SYSTEM:user_id"));
            if ("p_region_key".equalsIgnoreCase(parameterName) && !("REGION".equals(sourceType) && "region_key".equals(sourceKey))) errors.add(issue(path, "SYSTEM_PARAMETER_REQUIRED", "p_region_key 必须映射为 REGION:region_key"));
            if ("p_component_key".equalsIgnoreCase(parameterName) && !("COMPONENT".equals(sourceType) && "component_key".equals(sourceKey))) errors.add(issue(path, "SYSTEM_PARAMETER_REQUIRED", "p_component_key 必须映射为 COMPONENT:component_key"));
            if ("p_drill_field".equalsIgnoreCase(parameterName) && !("DRILL".equals(sourceType) && "field".equals(sourceKey))) errors.add(issue(path, "SYSTEM_PARAMETER_REQUIRED", "p_drill_field 必须映射为 DRILL:field"));
            if ("p_drill_value".equalsIgnoreCase(parameterName) && !("DRILL".equals(sourceType) && "value".equals(sourceKey))) errors.add(issue(path, "SYSTEM_PARAMETER_REQUIRED", "p_drill_value 必须映射为 DRILL:value"));
            if ("CONTROL".equals(sourceType)) {
                String controlKey = sourceKey.contains(".") ? sourceKey.substring(0, sourceKey.indexOf('.')) : sourceKey;
                if (!controlKeys.contains(controlKey)) errors.add(issue(path + ".sourceKey", "CONTROL_NOT_FOUND", "参数来源控件不存在"));
            }
        }
    }

    private static String optionSqlIssue(String sql) {
        if (sql == null || sql.isBlank()) return "请填写选项 SQL，且必须返回 value、label 两列";
        String normalized = sql.trim().toLowerCase(java.util.Locale.ROOT);
        if (!(normalized.startsWith("select ") || normalized.startsWith("with "))) return "SQL 选项必须以 SELECT 或 WITH 开头";
        if (normalized.contains(";")) return "SQL 选项不能包含分号，请去掉末尾 ;";
        if (normalized.contains("--") || normalized.contains("/*")) return "SQL 选项不能包含注释";
        if (java.util.regex.Pattern.compile("\\b(insert|update|delete|drop|alter|create|grant|revoke|call|outfile|dumpfile|for\\s+update|lock\\s+in\\s+share)\\b").matcher(normalized).find()) {
            return "SQL 选项只允许单条只读 SELECT/CTE";
        }
        try {
            if (!(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql.replace(":currentUserId", "?")) instanceof net.sf.jsqlparser.statement.select.Select)) {
                return "SQL 选项只允许单条只读 SELECT/CTE";
            }
            return null;
        } catch (Exception ex) {
            return "SQL 选项语法无效，请检查是否写了分号或非法语句";
        }
    }

    private static boolean safeSelect(String sql) {
        return optionSqlIssue(sql) == null;
    }

    private void validateComponents(JsonNode components, List<ReportDtos.ValidationIssue> errors) {
        if (!components.isArray() || components.isEmpty()) {
            errors.add(issue("components", "REGION_REQUIRED", "报表至少需要一个组件或表格区域"));
            return;
        }
        Set<String> keys = new HashSet<>();
        int tableCount = 0;
        for (int i = 0; i < components.size(); i++) {
            JsonNode component = components.get(i);
            String path = "components[" + i + "]";
            String key = component.path("componentKey").asText();
            if (key.isBlank() || !keys.add(key)) errors.add(issue(path + ".componentKey", "COMPONENT_KEY_INVALID", "组件键不能为空且必须唯一"));
            String region = component.path("regionType").asText();
            if ("TABLE".equals(region)) tableCount++;
            else if (!"COMPONENT".equals(region)) errors.add(issue(path + ".regionType", "REGION_TYPE_INVALID", "区域类型不合法"));
            JsonNode layout = component.path("layout");
            if ("COMPONENT".equals(region)) {
                int x=layout.path("x").asInt(0), y=layout.path("y").asInt(0), w=layout.path("w").asInt(6), h=layout.path("h").asInt(8);
                if (x<0 || x>11 || y<0 || w<1 || w>12 || h<1 || h>100 || x+w>12) errors.add(issue(path+".layout","LAYOUT_INVALID","组件栅格坐标或尺寸超出边界"));
            }
            JsonNode routes = component.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                errors.add(issue(path + ".routes", "ROOT_ROUTE_REQUIRED", "区域必须包含 ROOT 路由"));
                continue;
            }
            Set<String> routeCodes = new HashSet<>();
            java.util.Map<String, Set<String>> routeFields = new java.util.HashMap<>();
            int rootCount = 0;
            for (int j = 0; j < routes.size(); j++) {
                JsonNode route = routes.get(j);
                String routeCode = route.path("routeCode").asText();
                if (!routeCodes.add(routeCode)) errors.add(issue(path + ".routes[" + j + "].routeCode", "ROUTE_DUPLICATE", "路由编码必须唯一"));
                if ("ROOT".equals(routeCode)) rootCount++;
                String view = route.path("viewType").asText();
                if ("TABLE".equals(region) && !"TABLE".equals(view)) errors.add(issue(path + ".routes[" + j + "].viewType", "VIEW_TYPE_INVALID", "表格区域路由只能使用 TABLE"));
                if ("COMPONENT".equals(region) && !Set.of("BAR", "STACKED_BAR", "HORIZONTAL_BAR", "LINE", "AREA", "PIE", "DONUT", "GAUGE", "KPI").contains(view)) errors.add(issue(path + ".routes[" + j + "].viewType", "VIEW_TYPE_INVALID", "组件区域视图类型不合法"));
                Set<String> fieldNames = new HashSet<>();
                JsonNode fields = route.path("fields");
                if (!fields.isArray()) errors.add(issue(path + ".routes[" + j + "].fields", "FIELDS_INVALID", "字段必须是数组"));
                else for (int k = 0; k < fields.size(); k++) {
                    String name = fields.get(k).path("physicalName").asText();
                    if (name.isBlank() || !fieldNames.add(name)) errors.add(issue(path + ".routes[" + j + "].fields[" + k + "].physicalName", "FIELD_DUPLICATE", "物理字段名不能为空且必须唯一"));
                }
                if (fields.isArray()) for (int k=0;k<fields.size();k++) {
                    String style=fields.get(k).path("styleIndicatorField").asText();
                    if (!style.isBlank() && !fieldNames.contains(style)) errors.add(issue(path+".routes["+j+"].fields["+k+"].styleIndicatorField","FIELD_NOT_FOUND","样式字段不存在"));
                }
                routeFields.put(routeCode, fieldNames);
            }
            if (rootCount != 1) errors.add(issue(path + ".routes", "ROOT_ROUTE_INVALID", "区域必须且只能包含一个 ROOT 路由"));
            if ("COMPONENT".equals(region) && routes.size() != 1) errors.add(issue(path + ".routes", "COMPONENT_ROUTE_INVALID", "上方组件第一版只能包含 ROOT 路由"));
            if ("TABLE".equals(region)) validateEdges(routes, routeCodes, routeFields, path, errors);
            if ("TABLE".equals(region)) validateAcyclic(routes, path, errors);
        }
        if (tableCount > 1) errors.add(issue("components", "TABLE_REGION_DUPLICATE", "下方表格区域最多一个"));
    }

    private void validateAcyclic(JsonNode routes, String path, List<ReportDtos.ValidationIssue> errors) {
        java.util.Map<String,Set<String>> graph=new java.util.HashMap<>();
        routes.forEach(route->{Set<String> targets=new HashSet<>();route.path("drillEdges").forEach(edge->targets.add(edge.path("targetRouteCode").asText()));graph.put(route.path("routeCode").asText(),targets);});
        Set<String> visiting=new HashSet<>(),done=new HashSet<>();
        for(String node:graph.keySet()) if(cycle(node,graph,visiting,done)){errors.add(issue(path+".routes","DRILL_CYCLE","钻取路由不能形成环"));break;}
    }
    private boolean cycle(String node,java.util.Map<String,Set<String>> graph,Set<String> visiting,Set<String> done){if(done.contains(node))return false;if(!visiting.add(node))return true;for(String target:graph.getOrDefault(node,Set.of()))if(cycle(target,graph,visiting,done))return true;visiting.remove(node);done.add(node);return false;}

    private void validateEdges(JsonNode routes, Set<String> routeCodes, java.util.Map<String, Set<String>> routeFields,
                               String componentPath, List<ReportDtos.ValidationIssue> errors) {
        for (int i = 0; i < routes.size(); i++) {
            JsonNode route = routes.get(i); String source = route.path("routeCode").asText();
            JsonNode edges = route.path("drillEdges");
            if (!edges.isArray()) { errors.add(issue(componentPath + ".routes[" + i + "].drillEdges", "DRILL_INVALID", "钻取边必须是数组")); continue; }
            for (int j = 0; j < edges.size(); j++) {
                JsonNode edge = edges.get(j); String edgePath = componentPath + ".routes[" + i + "].drillEdges[" + j + "]";
                if (!routeCodes.contains(edge.path("targetRouteCode").asText())) errors.add(issue(edgePath + ".targetRouteCode", "ROUTE_NOT_FOUND", "目标路由不存在"));
                if (!routeFields.getOrDefault(source, Set.of()).contains(edge.path("triggerField").asText())) errors.add(issue(edgePath + ".triggerField", "FIELD_NOT_FOUND", "触发字段未在源路由登记"));
                if (!routeFields.getOrDefault(source, Set.of()).contains(edge.path("payloadField").asText())) errors.add(issue(edgePath + ".payloadField", "FIELD_NOT_FOUND", "载荷字段未在源路由登记"));
                if (edge.path("routeValue").asText().isBlank()) errors.add(issue(edgePath + ".routeValue", "FIELD_INVALID", "路由值不能为空"));
            }
        }
    }

    private static void requiredText(JsonNode parent, String field, int max, List<ReportDtos.ValidationIssue> errors) {
        String value = parent.path(field).asText();
        if (value.isBlank() || value.length() > max) errors.add(issue("baseInfo." + field, "FIELD_INVALID", field + " 不能为空且长度不能超过 " + max));
    }
    private static void enumValue(JsonNode parent, String field, Set<String> allowed, List<ReportDtos.ValidationIssue> errors) {
        if (!allowed.contains(parent.path(field).asText())) errors.add(issue("baseInfo." + field, "FIELD_INVALID", field + " 取值不合法"));
    }
    private static void range(JsonNode parent, String field, int min, int max, List<ReportDtos.ValidationIssue> errors) {
        int value = parent.path(field).asInt(-1);
        if (value < min || value > max) errors.add(issue("baseInfo." + field, "FIELD_INVALID", field + " 必须在 " + min + " 到 " + max + " 之间"));
    }
    private static ReportDtos.ValidationIssue issue(String path, String code, String message) {
        return new ReportDtos.ValidationIssue(path, code, message);
    }
}
