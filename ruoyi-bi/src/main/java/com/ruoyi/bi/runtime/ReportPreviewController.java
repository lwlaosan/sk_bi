package com.ruoyi.bi.runtime;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.bi.api.ApiResponse;
import com.ruoyi.bi.api.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Designer-only preview of the latest saved configuration, including disabled reports. */
@RestController
@RequestMapping("/api/bi/admin/report-previews")
@PreAuthorize("@ss.hasPermi('bi:report:design')")
public class ReportPreviewController {
    private final RuntimeReportService service;

    public ReportPreviewController(RuntimeReportService service) { this.service = service; }

    @GetMapping("/{uuid}")
    ApiResponse<ObjectNode> configuration(@PathVariable String uuid, HttpServletRequest request) {
        return ApiResponse.ok(service.configuration(uuid, true), TraceIdFilter.current(request));
    }

    @GetMapping("/{uuid}/controls/{controlKey}/options")
    ApiResponse<RuntimeDtos.OptionResult> options(@PathVariable String uuid, @PathVariable String controlKey, HttpServletRequest request) {
        return ApiResponse.ok(service.options(uuid, controlKey, true), TraceIdFilter.current(request));
    }

    @PostMapping("/{uuid}/components/{componentKey}/query")
    ApiResponse<RuntimeDtos.QueryResult> query(@PathVariable String uuid, @PathVariable String componentKey,
                                              @Valid @RequestBody RuntimeDtos.QueryRequest body, HttpServletRequest request) {
        String traceId = TraceIdFilter.current(request);
        return ApiResponse.ok(service.query(uuid, componentKey, body, traceId, true), traceId);
    }
}
