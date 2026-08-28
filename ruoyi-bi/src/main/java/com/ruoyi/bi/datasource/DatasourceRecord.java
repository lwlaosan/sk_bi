package com.ruoyi.bi.datasource;

import java.time.LocalDateTime;
import java.util.Map;

public record DatasourceRecord(long id, String datasourceName, String host, int port, String databaseName,
                               String username, String passwordCiphertext, Map<String, Object> connectionProps,
                               int credentialVersion, DatasourceStatus status, String remark,
                               LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion) {
    public DatabaseType databaseType() {
        Object value = connectionProps == null ? null : connectionProps.get("databaseType");
        if (value == null) return DatabaseType.MYSQL;
        try { return DatabaseType.valueOf(String.valueOf(value).toUpperCase()); }
        catch (IllegalArgumentException ex) { return DatabaseType.MYSQL; }
    }

    public String schemaName() {
        Object value = connectionProps == null ? null : connectionProps.get("schema");
        if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        return switch (databaseType()) {
            case SQLSERVER -> "dbo";
            case POSTGRESQL -> "public";
            case MYSQL -> null;
        };
    }
}
