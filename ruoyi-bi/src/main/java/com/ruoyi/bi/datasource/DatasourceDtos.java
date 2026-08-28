package com.ruoyi.bi.datasource;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DatasourceDtos {
    private DatasourceDtos() {}

    public record SaveRequest(
        @NotBlank @Size(max = 100) String datasourceName,
        DatabaseType databaseType,
        @NotBlank @Size(max = 255) String host,
        @Min(1) @Max(65535) int port,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_$-]{1,128}") String databaseName,
        @NotBlank @Size(max = 128) String username,
        @Size(max = 4096) String password,
        Map<String, Object> connectionProps,
        @NotNull DatasourceStatus status,
        @Size(max = 500) String remark,
        List<@Positive Long> roleIds,
        List<@Positive Long> userIds,
        Long expectedRowVersion
    ) {
        public DatabaseType effectiveDatabaseType() {
            return databaseType == null ? DatabaseType.MYSQL : databaseType;
        }
    }

    public record View(String id, String datasourceName, DatabaseType databaseType, String host, int port, String databaseName,
                       String username, boolean hasPassword, Map<String, Object> connectionProps,
                       int credentialVersion, DatasourceStatus status, String remark,
                       List<String> roleIds, List<String> userIds,
                       LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion) {}

    public record Page(List<View> items, int page, int pageSize, long total) {}
    public record ConnectionTest(boolean success, long elapsedMs, String category, List<String> warnings) {}
    public record ProcedureSummary(String procedureName) {}
    public record ProcedureParameter(int ordinal, String name, String mode, String mysqlDataType, String dtdIdentifier) {}
    public record ProcedureMetadata(String procedureName, String signatureHash, boolean supported,
                                    List<ProcedureParameter> parameters, List<String> unsupportedReasons) {}
}
