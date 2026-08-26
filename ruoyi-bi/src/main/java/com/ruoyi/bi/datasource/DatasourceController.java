package com.ruoyi.bi.datasource;

import com.ruoyi.bi.api.ApiResponse;
import com.ruoyi.bi.api.TraceIdFilter;
import com.ruoyi.bi.security.CurrentUser;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bi/admin/datasources")
public class DatasourceController {
    private final DatasourceService service;
    private final CurrentUser currentUser;

    public DatasourceController(DatasourceService service, CurrentUser currentUser) {
        this.service = service; this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('bi:datasource:list')")
    ApiResponse<DatasourceDtos.Page> page(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int pageSize,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) DatasourceStatus status,
                                          HttpServletRequest request) {
        return ApiResponse.ok(service.page(page, pageSize, keyword, status), TraceIdFilter.current(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('bi:datasource:list')")
    ApiResponse<DatasourceDtos.View> get(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.get(id), TraceIdFilter.current(request));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('bi:datasource:manage')")
    @Log(title = "BI数据源", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    ApiResponse<DatasourceDtos.View> create(@Valid @RequestBody DatasourceDtos.SaveRequest body,
                                            HttpServletRequest request) {
        return ApiResponse.ok(service.create(body, currentUser.id()), TraceIdFilter.current(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('bi:datasource:manage')")
    @Log(title = "BI数据源", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    ApiResponse<DatasourceDtos.View> update(@PathVariable long id, @Valid @RequestBody DatasourceDtos.SaveRequest body,
                                            HttpServletRequest request) {
        return ApiResponse.ok(service.update(id, body, currentUser.id()), TraceIdFilter.current(request));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("@ss.hasPermi('bi:datasource:manage')")
    @Log(title = "BI数据源连接测试", businessType = BusinessType.OTHER, isSaveRequestData = false, isSaveResponseData = false)
    ApiResponse<DatasourceDtos.ConnectionTest> test(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.test(id), TraceIdFilter.current(request));
    }

    @GetMapping("/{id}/procedures")
    @PreAuthorize("@ss.hasPermi('bi:datasource:list')")
    ApiResponse<?> procedures(@PathVariable long id, @RequestParam(required = false) String keyword, HttpServletRequest request) {
        return ApiResponse.ok(service.procedures(id, keyword), TraceIdFilter.current(request));
    }

    @GetMapping("/{id}/procedures/{procedureName}/parameters")
    @PreAuthorize("@ss.hasPermi('bi:datasource:list')")
    ApiResponse<?> parameters(@PathVariable long id, @PathVariable String procedureName, HttpServletRequest request) {
        return ApiResponse.ok(service.parameters(id, procedureName), TraceIdFilter.current(request));
    }
}
