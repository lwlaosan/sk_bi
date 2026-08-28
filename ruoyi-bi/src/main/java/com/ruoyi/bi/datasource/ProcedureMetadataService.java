package com.ruoyi.bi.datasource;

import com.ruoyi.bi.api.BiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class ProcedureMetadataService {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "char", "nchar", "varchar", "nvarchar", "text", "ntext", "tinytext", "mediumtext", "longtext",
        "uuid", "uniqueidentifier", "xml", "clob",
        "tinyint", "smallint", "mediumint", "int", "int2", "int4", "int8", "integer", "bigint", "serial", "bigserial",
        "decimal", "numeric", "money", "smallmoney", "float", "float4", "float8", "double", "real",
        "date", "datetime", "datetime2", "smalldatetime", "timestamp", "timestamptz", "time",
        "json", "jsonb", "boolean", "bool", "bit");
    private final BusinessConnectionFactory connections;

    public ProcedureMetadataService(BusinessConnectionFactory connections) { this.connections = connections; }

    public List<DatasourceDtos.ProcedureSummary> list(DatasourceRecord source, String keyword) {
        if (source.databaseType() == DatabaseType.MYSQL) return listMySql(source, keyword);
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        try (Connection connection = connections.open(source);
             ResultSet rs = connection.getMetaData().getProcedures(catalog(source), source.schemaName(), "%")) {
            List<DatasourceDtos.ProcedureSummary> result = new ArrayList<>();
            while (rs.next() && result.size() < 500) {
                String name = qualified(source, rs.getString("PROCEDURE_SCHEM"), rs.getString("PROCEDURE_NAME"));
                if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query))
                    result.add(new DatasourceDtos.ProcedureSummary(name));
            }
            result.sort(Comparator.comparing(DatasourceDtos.ProcedureSummary::procedureName));
            return result;
        } catch (SQLException ex) { throw unavailable(); }
    }

    public DatasourceDtos.ProcedureMetadata parameters(DatasourceRecord source, String requestedName) {
        if (source.databaseType() == DatabaseType.MYSQL) return parametersMySql(source, requestedName);
        Name name = parseName(source, requestedName);
        try (Connection connection = connections.open(source)) {
            DatabaseMetaData metadata = connection.getMetaData();
            List<DatasourceDtos.ProcedureParameter> parameters = new ArrayList<>();
            try (ResultSet rs = metadata.getProcedureColumns(catalog(source), name.schema(), name.procedure(), "%")) {
                while (rs.next()) {
                    String mode = mode(rs.getShort("COLUMN_TYPE"));
                    if (mode == null) continue;
                    String type = normalizeType(rs.getString("TYPE_NAME"));
                    parameters.add(new DatasourceDtos.ProcedureParameter(
                        Math.max(1, rs.getInt("ORDINAL_POSITION")), rs.getString("COLUMN_NAME"), mode, type, type));
                }
            }
            parameters.sort(Comparator.comparingInt(DatasourceDtos.ProcedureParameter::ordinal));
            if (parameters.isEmpty() && !procedureExists(metadata, source, name)) throw notFound();
            List<String> reasons = new ArrayList<>();
            for (var parameter : parameters) {
                if (!"IN".equalsIgnoreCase(parameter.mode())) reasons.add("仅支持 IN 参数: " + parameter.name());
                if (!SUPPORTED_TYPES.contains(parameter.mysqlDataType()))
                    reasons.add("不支持参数类型 " + parameter.mysqlDataType() + ": " + parameter.name());
            }
            return new DatasourceDtos.ProcedureMetadata(requestedName, ProcedureSignature.sha256(parameters),
                reasons.isEmpty(), parameters, List.copyOf(reasons));
        } catch (BiException ex) { throw ex; }
        catch (SQLException ex) { throw unavailable(); }
    }

    private List<DatasourceDtos.ProcedureSummary> listMySql(DatasourceRecord source, String keyword) {
        String sql = """
            SELECT ROUTINE_NAME FROM INFORMATION_SCHEMA.ROUTINES
            WHERE ROUTINE_SCHEMA=? AND ROUTINE_TYPE='PROCEDURE' AND (?='' OR ROUTINE_NAME LIKE ?)
            ORDER BY ROUTINE_NAME LIMIT 501
            """;
        try (Connection connection = connections.open(source); PreparedStatement statement = connection.prepareStatement(sql)) {
            String query = keyword == null ? "" : keyword.trim();
            statement.setString(1, source.databaseName()); statement.setString(2, query); statement.setString(3, "%" + query + "%");
            statement.setQueryTimeout(10);
            List<DatasourceDtos.ProcedureSummary> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next() && result.size() < 500) result.add(new DatasourceDtos.ProcedureSummary(rs.getString(1)));
            }
            return result;
        } catch (SQLException ex) { throw unavailable(); }
    }

    private DatasourceDtos.ProcedureMetadata parametersMySql(DatasourceRecord source, String requestedName) {
        if (requestedName == null || requestedName.length() > 128) throw notFound();
        String sql = """
            SELECT ORDINAL_POSITION,PARAMETER_MODE,PARAMETER_NAME,DATA_TYPE,DTD_IDENTIFIER
            FROM INFORMATION_SCHEMA.PARAMETERS
            WHERE SPECIFIC_SCHEMA=? AND SPECIFIC_NAME=? ORDER BY ORDINAL_POSITION
            """;
        try (Connection connection = connections.open(source); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source.databaseName()); statement.setString(2, requestedName); statement.setQueryTimeout(10);
            List<DatasourceDtos.ProcedureParameter> parameters = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) parameters.add(new DatasourceDtos.ProcedureParameter(rs.getInt(1), rs.getString(3),
                    rs.getString(2), rs.getString(4), rs.getString(5)));
            }
            if (parameters.isEmpty() && !mySqlProcedureExists(connection, source.databaseName(), requestedName)) throw notFound();
            List<String> reasons = unsupportedReasons(parameters);
            return new DatasourceDtos.ProcedureMetadata(requestedName, ProcedureSignature.sha256(parameters),
                reasons.isEmpty(), parameters, List.copyOf(reasons));
        } catch (BiException ex) { throw ex; }
        catch (SQLException ex) { throw unavailable(); }
    }

    private boolean mySqlProcedureExists(Connection connection, String schema, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA=? AND ROUTINE_TYPE='PROCEDURE' AND ROUTINE_NAME=?")) {
            statement.setString(1, schema); statement.setString(2, name);
            try (ResultSet rs = statement.executeQuery()) { return rs.next(); }
        }
    }

    private List<String> unsupportedReasons(List<DatasourceDtos.ProcedureParameter> parameters) {
        List<String> reasons = new ArrayList<>();
        for (var parameter : parameters) {
            if (!"IN".equalsIgnoreCase(parameter.mode())) reasons.add("仅支持 IN 参数: " + parameter.name());
            if (!SUPPORTED_TYPES.contains(normalizeType(parameter.mysqlDataType())))
                reasons.add("不支持参数类型 " + parameter.mysqlDataType() + ": " + parameter.name());
        }
        return reasons;
    }

    private boolean procedureExists(DatabaseMetaData metadata, DatasourceRecord source, Name name) throws SQLException {
        try (ResultSet rs = metadata.getProcedures(catalog(source), name.schema(), name.procedure())) { return rs.next(); }
    }

    private Name parseName(DatasourceRecord source, String requestedName) {
        if (requestedName == null || requestedName.isBlank() || requestedName.length() > 257) throw notFound();
        int separator = requestedName.lastIndexOf('.');
        String schema = separator < 0 ? source.schemaName() : requestedName.substring(0, separator);
        String procedure = separator < 0 ? requestedName : requestedName.substring(separator + 1);
        if (!identifier(procedure) || (schema != null && !identifier(schema))) throw notFound();
        return new Name(schema, procedure);
    }

    private static String catalog(DatasourceRecord source) { return source.databaseName(); }
    private static String qualified(DatasourceRecord source, String schema, String name) {
        if (source.databaseType() == DatabaseType.MYSQL || schema == null || schema.isBlank()) return name;
        return schema + "." + name;
    }
    private static boolean identifier(String value) { return value != null && value.matches("[A-Za-z0-9_$]{1,128}"); }
    private static String normalizeType(String value) {
        if (value == null) return "";
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        int bracket = normalized.indexOf('(');
        return bracket < 0 ? normalized : normalized.substring(0, bracket).trim();
    }
    private static String mode(short value) {
        return switch (value) {
            case DatabaseMetaData.procedureColumnIn -> "IN";
            case DatabaseMetaData.procedureColumnInOut -> "INOUT";
            case DatabaseMetaData.procedureColumnOut -> "OUT";
            default -> null;
        };
    }

    private record Name(String schema, String procedure) {}
    private static BiException unavailable() { return new BiException(HttpStatus.SERVICE_UNAVAILABLE, "BI_DATASOURCE_UNAVAILABLE", "数据源不可连接或元数据不可读取"); }
    private static BiException notFound() { return new BiException(HttpStatus.NOT_FOUND, "BI_REQUEST_INVALID", "存储过程不存在"); }
}
