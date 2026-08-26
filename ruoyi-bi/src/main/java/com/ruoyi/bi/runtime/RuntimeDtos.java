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
}
