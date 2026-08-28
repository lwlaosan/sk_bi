package com.ruoyi.bi.runtime;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.bi.api.ApiResponse;
import com.ruoyi.bi.api.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/bi/runtime/reports")
public class RuntimeReportController {
    private final RuntimeReportService service; private final ExcelExportService exports; private final InsightService insights;
    public RuntimeReportController(RuntimeReportService service,ExcelExportService exports,InsightService insights){this.service=service;this.exports=exports;this.insights=insights;}

    @GetMapping("/{uuid}")
    @PreAuthorize("@ss.hasPermi('bi:report:view')")
    ApiResponse<ObjectNode> configuration(@PathVariable String uuid,HttpServletRequest request){return ApiResponse.ok(service.configuration(uuid),TraceIdFilter.current(request));}
    @GetMapping("/{uuid}/controls/{controlKey}/options")
    @PreAuthorize("@ss.hasPermi('bi:report:view')")
    ApiResponse<RuntimeDtos.OptionResult> options(@PathVariable String uuid,@PathVariable String controlKey,HttpServletRequest request){return ApiResponse.ok(service.options(uuid,controlKey),TraceIdFilter.current(request));}
    @PostMapping("/{uuid}/components/{componentKey}/query")
    @PreAuthorize("@ss.hasPermi('bi:report:view')")
    ApiResponse<RuntimeDtos.QueryResult> query(@PathVariable String uuid,@PathVariable String componentKey,@Valid @RequestBody RuntimeDtos.QueryRequest body,HttpServletRequest request){return ApiResponse.ok(service.query(uuid,componentKey,body,TraceIdFilter.current(request)),TraceIdFilter.current(request));}
    @PostMapping("/{uuid}/insight")
    @PreAuthorize("@ss.hasPermi('bi:report:view')")
    ApiResponse<RuntimeDtos.InsightResult> insight(@PathVariable String uuid,@Valid @RequestBody RuntimeDtos.InsightRequest body,HttpServletRequest request){return ApiResponse.ok(insights.generate(uuid,body),TraceIdFilter.current(request));}
    @GetMapping("/{uuid}/insights")
    @PreAuthorize("@ss.hasPermi('bi:report:view')")
    ApiResponse<RuntimeDtos.InsightHistoryPage> insightHistory(@PathVariable String uuid,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,HttpServletRequest request){
        return ApiResponse.ok(insights.history(uuid,page,pageSize),TraceIdFilter.current(request));
    }
    @GetMapping("/{uuid}/insights/{historyId}")
    @PreAuthorize("@ss.hasPermi('bi:report:view')")
    ApiResponse<RuntimeDtos.InsightHistoryDetail> insightHistoryDetail(@PathVariable String uuid,@PathVariable long historyId,HttpServletRequest request){
        return ApiResponse.ok(insights.historyDetail(uuid,historyId),TraceIdFilter.current(request));
    }

    @PostMapping("/{uuid}/components/{componentKey}/export")
    @PreAuthorize("@ss.hasPermi('bi:report:export')")
    void export(@PathVariable String uuid,@PathVariable String componentKey,@Valid @RequestBody RuntimeDtos.QueryRequest body,
                HttpServletRequest request,HttpServletResponse response) throws java.io.IOException {
        RuntimeDtos.QueryResult result=service.query(uuid,componentKey,body,TraceIdFilter.current(request)); byte[] bytes=exports.component(result);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+URLEncoder.encode(componentKey+".xlsx",StandardCharsets.UTF_8));
        response.setContentLength(bytes.length); response.getOutputStream().write(bytes);
    }

    @PostMapping("/{uuid}/export")
    @PreAuthorize("@ss.hasPermi('bi:report:export')")
    void exportReport(@PathVariable String uuid,@Valid @RequestBody RuntimeDtos.QueryRequest body,HttpServletRequest request,HttpServletResponse response)throws java.io.IOException{
        byte[] bytes=exports.report(service.queryAll(uuid,body,TraceIdFilter.current(request)));response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''report.xlsx");response.setContentLength(bytes.length);response.getOutputStream().write(bytes);
    }
}
