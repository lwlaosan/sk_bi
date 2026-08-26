package com.ruoyi.bi.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.bi.api.BiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class ProcedureParameterBinder {
    private final ObjectMapper mapper;
    public ProcedureParameterBinder(ObjectMapper mapper) { this.mapper = mapper; }

    public List<BoundParameter> resolve(JsonNode mappings, Context context) {
        List<JsonNode> ordered = new ArrayList<>(); mappings.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(node -> node.path("parameterOrdinal").asInt()));
        List<BoundParameter> result = new ArrayList<>();
        for (JsonNode mapping : ordered) {
            String type = mapping.path("mysqlDataType").asText();
            JsonNode raw = source(mapping, context);
            boolean preserveBlank = "CONSTANT".equals(mapping.path("sourceType").asText());
            result.add(convert(mapping.path("parameterOrdinal").asInt(), type, raw, preserveBlank));
        }
        return result;
    }

    public void bind(PreparedStatement statement, List<BoundParameter> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            BoundParameter parameter = parameters.get(i);
            if (parameter.value() == null) statement.setNull(i + 1, parameter.jdbcType());
            else statement.setObject(i + 1, parameter.value(), parameter.jdbcType());
        }
    }

    private JsonNode source(JsonNode mapping, Context c) {
        String source = mapping.path("sourceType").asText(); String key = mapping.path("sourceKey").asText();
        return switch (source) {
            case "SYSTEM" -> mapper.valueToTree(c.userId());
            case "REGION" -> mapper.valueToTree(c.regionKey());
            case "COMPONENT" -> mapper.valueToTree(c.componentKey());
            case "DRILL" -> "field".equals(key) ? mapper.valueToTree(c.drillField()) : c.drillValue();
            case "CONTROL" -> control(c.controls(), key);
            case "CONSTANT" -> mapper.valueToTree(mapping.path("constantValue").asText());
            case "NULL" -> mapper.nullNode();
            default -> throw invalid("不支持的参数来源");
        };
    }

    private JsonNode control(Map<String, JsonNode> controls, String key) {
        String[] parts = key.split("\\.", 2); JsonNode value = controls.get(parts[0]);
        if (value == null) return mapper.nullNode();
        return parts.length == 1 ? value : value.path(parts[1]);
    }

    private BoundParameter convert(int ordinal, String type, JsonNode raw, boolean preserveBlank) {
        try {
            String normalized = type.toLowerCase();
            if (raw == null || raw.isNull() || raw.isMissingNode()
                    || (!preserveBlank && raw.isTextual() && raw.asText().isBlank()))
                return new BoundParameter(ordinal, jdbcType(normalized), null);
            if (normalized.equals("json")) return new BoundParameter(ordinal, Types.LONGVARCHAR, mapper.writeValueAsString(raw));
            if (normalized.equals("date")) return new BoundParameter(ordinal, Types.DATE, LocalDate.parse(raw.asText()));
            if (normalized.contains("datetime") || normalized.contains("timestamp")) return new BoundParameter(ordinal, Types.TIMESTAMP, LocalDateTime.parse(raw.asText()));
            if (normalized.matches("tinyint|smallint|mediumint|int|integer|bigint")) return new BoundParameter(ordinal, Types.BIGINT, raw.longValue());
            if (normalized.matches("decimal|numeric|float|double|real")) return new BoundParameter(ordinal, Types.DECIMAL, new BigDecimal(raw.asText()));
            if (normalized.equals("boolean") || normalized.equals("bool") || normalized.equals("bit")) return new BoundParameter(ordinal, Types.BOOLEAN, raw.asBoolean());
            return new BoundParameter(ordinal, Types.VARCHAR, raw.isValueNode() ? raw.asText() : mapper.writeValueAsString(raw));
        } catch (Exception ex) { throw invalid("参数 " + ordinal + " 类型转换失败"); }
    }

    private static int jdbcType(String type) {
        if (type.equals("date")) return Types.DATE;
        if (type.contains("datetime") || type.contains("timestamp")) return Types.TIMESTAMP;
        if (type.matches("tinyint|smallint|mediumint|int|integer|bigint")) return Types.BIGINT;
        if (type.matches("decimal|numeric|float|double|real")) return Types.DECIMAL;
        if (type.equals("boolean") || type.equals("bool") || type.equals("bit")) return Types.BOOLEAN;
        if (type.equals("json")) return Types.LONGVARCHAR;
        return Types.VARCHAR;
    }
    private static BiException invalid(String message) { return new BiException(HttpStatus.BAD_REQUEST, "BI_REQUEST_INVALID", message); }
    public record Context(long userId, String regionKey, String componentKey, Map<String,JsonNode> controls,
                          String drillField, JsonNode drillValue) {}
    public record BoundParameter(int ordinal, int jdbcType, Object value) {}
}
