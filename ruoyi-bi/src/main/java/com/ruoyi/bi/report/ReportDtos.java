package com.ruoyi.bi.report;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

public final class ReportDtos {
    private ReportDtos() {}

    public record CreateRequest(
        @NotBlank @Size(max = 150) String reportName,
        @Size(max = 1000) String description,
        @Positive long defaultDatasourceId,
        @NotBlank @Size(max = 128) String defaultProcedureName,
        @Min(1) @Max(200_000) Integer maxRows,
        @Min(1) @Max(600) Integer timeoutSeconds
    ) {}

    public record StatusRequest(@NotNull ReportStatus status, @Positive long expectedVersion) {}

    public record Summary(String id, String reportUuid, String reportName, String description,
                          ReportStatus status, String defaultDatasourceId, String defaultProcedureName,
                          int componentCount, long currentConfigVersion, String createdBy,
                          LocalDateTime updatedAt, long rowVersion, String accessUrl) {}

    public record Page(List<Summary> items, int page, int pageSize, long total) {}

    public record Created(String reportId, String reportUuid, long configVersion) {}
    public record ValidationIssue(String path, String code, String message) {}
    public record ValidationResult(boolean valid, List<ValidationIssue> errors, List<ValidationIssue> warnings) {}
    public record Saved(String reportId, String reportUuid, long configVersion, LocalDateTime effectiveAt) {}
    public record VersionSummary(long versionNo, String operationType, Long sourceVersion,
                                 String changeSummary, String createdBy, LocalDateTime createdAt) {}
    public record VersionPage(List<VersionSummary> items, int page, int pageSize, long total) {}
    public record VersionDetail(long versionNo, String operationType, Long sourceVersion,
                                String changeSummary, String snapshotSha256, JsonNode snapshot,
                                String createdBy, LocalDateTime createdAt) {}
    public record RollbackRequest(@Positive long expectedVersion, @Size(max = 500) String changeSummary) {}
    public record DiffItem(String path, JsonNode before, JsonNode after) {}
    public record VersionDiff(long fromVersion, long toVersion, List<DiffItem> changes) {}
}
