package com.ruoyi.bi.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;

public final class RuntimeDtos {
    private RuntimeDtos() {}
    public record QueryRequest(@Positive long configVersion, Map<String, JsonNode> controls,
                               JsonNode drill, @NotBlank String requestId) {}
    public record OptionItem(String value, String label) {}
    public record OptionResult(List<OptionItem> items, boolean truncated) {}
    public record QueryResult(String requestId, String componentKey, String regionType, JsonNode route,
                              JsonNode fields, List<Map<String,Object>> rows, int rowCount,
                              boolean truncated, int limit, long elapsedMs, String traceId) {}
    public record InsightField(@NotBlank String physicalName, String displayName, String dataType) {}
    public record InsightDataset(@NotBlank String componentKey, String componentName, String routeName,
                                 String scopeType, @NotNull List<String> levelPath,
                                 @NotNull List<InsightField> fields,
                                 @NotNull List<Map<String,Object>> rows, int rowCount, boolean truncated) {}
    public record InsightRequest(@Positive long configVersion, Map<String,Object> controls,
                                 @NotNull List<InsightDataset> datasets, @NotBlank String requestId) {}
    public record InsightResult(String historyId, String requestId, String content, String provider, String model,
                                String generatedAt, int inputRows, String routeSummary, String generatedByName) {}
    public record InsightHistorySummary(String id, String provider, String model, String routeSummary,
                                        int inputRows, String generatedBy, String generatedAt) {}
    public record InsightHistoryDetail(String id, long configVersion, String requestId, String provider,
                                       String model, String content, JsonNode contextSnapshot,
                                       String routeSummary, int inputRows, String generatedBy,
                                       String generatedAt) {}
    public record InsightHistoryPage(List<InsightHistorySummary> items, int page, int pageSize, long total) {}
}
