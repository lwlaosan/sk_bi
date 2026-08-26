package com.ruoyi.bi.datasource;

import com.ruoyi.bi.api.BiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class ProcedureMetadataService {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "char", "varchar", "tinytext", "text", "mediumtext", "longtext",
        "tinyint", "smallint", "mediumint", "int", "integer", "bigint",
        "decimal", "numeric", "float", "double", "date", "datetime", "timestamp", "time", "json", "boolean", "bit");
    private final BusinessConnectionFactory connections;

    public ProcedureMetadataService(BusinessConnectionFactory connections) { this.connections = connections; }

    public List<DatasourceDtos.ProcedureSummary> list(DatasourceRecord source, String keyword) {
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

    public DatasourceDtos.ProcedureMetadata parameters(DatasourceRecord source, String requestedName) {
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
            if (parameters.isEmpty() && !procedureExists(connection, source.databaseName(), requestedName)) throw notFound();
            List<String> reasons = new ArrayList<>();
            for (var parameter : parameters) {
                if (!"IN".equalsIgnoreCase(parameter.mode())) reasons.add("仅支持 IN 参数: " + parameter.name());
                if (!SUPPORTED_TYPES.contains(parameter.mysqlDataType().toLowerCase(Locale.ROOT)))
                    reasons.add("不支持参数类型 " + parameter.mysqlDataType() + ": " + parameter.name());
            }
            return new DatasourceDtos.ProcedureMetadata(requestedName, ProcedureSignature.sha256(parameters),
                reasons.isEmpty(), parameters, List.copyOf(reasons));
        } catch (BiException ex) { throw ex; }
        catch (SQLException ex) { throw unavailable(); }
    }

    private boolean procedureExists(Connection connection, String schema, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA=? AND ROUTINE_TYPE='PROCEDURE' AND ROUTINE_NAME=?")) {
            statement.setString(1, schema); statement.setString(2, name);
            try (ResultSet rs = statement.executeQuery()) { return rs.next(); }
        }
    }

    private static BiException unavailable() { return new BiException(HttpStatus.SERVICE_UNAVAILABLE, "BI_DATASOURCE_UNAVAILABLE", "数据源不可连接或元数据不可读取"); }
    private static BiException notFound() { return new BiException(HttpStatus.NOT_FOUND, "BI_REQUEST_INVALID", "存储过程不存在"); }
}

