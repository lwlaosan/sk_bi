package com.ruoyi.bi.report;

import com.ruoyi.bi.api.ApiResponse;
import com.ruoyi.bi.api.TraceIdFilter;
import com.ruoyi.bi.security.CurrentUser;
import com.ruoyi.bi.datasource.SubjectType;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/bi/admin/reports")
public class ReportController {
    private final ReportService service;
    private final CurrentUser currentUser;
    private final AclDirectoryService aclDirectory;

    public ReportController(ReportService service, CurrentUser currentUser, AclDirectoryService aclDirectory) {
        this.service = service; this.currentUser = currentUser; this.aclDirectory = aclDirectory;
    }

    @GetMapping("/acl-subjects")
    @PreAuthorize("@ss.hasPermi('bi:report:design')")
    ApiResponse<?> aclSubjects(@RequestParam SubjectType type,
                               @RequestParam(required = false) String keyword,
                               HttpServletRequest request) {
        return ApiResponse.ok(aclDirectory.search(type, keyword), TraceIdFilter.current(request));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('bi:report:list')")
    ApiResponse<ReportDtos.Page> page(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int pageSize,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) ReportStatus status,
                                      @RequestParam(required = false) Long createdBy,
                                      HttpServletRequest request) {
        return ApiResponse.ok(service.page(page, pageSize, keyword, status, createdBy), TraceIdFilter.current(request));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('bi:report:create')")
    @Log(title = "BI报表", businessType = BusinessType.INSERT, isSaveResponseData = false)
    ApiResponse<ReportDtos.Created> create(@Valid @RequestBody ReportDtos.CreateRequest body,
                                           HttpServletRequest request) {
        return ApiResponse.ok(service.create(body, currentUser.id()), TraceIdFilter.current(request));
    }

    @GetMapping("/{id}/configuration")
    @PreAuthorize("@ss.hasPermi('bi:report:design')")
    ApiResponse<ObjectNode> configuration(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.configuration(id), TraceIdFilter.current(request));
    }

    @PostMapping("/{id}/configuration/validate")
    @PreAuthorize("@ss.hasPermi('bi:report:design')")
    ApiResponse<ReportDtos.ValidationResult> validate(@PathVariable long id, @RequestBody ObjectNode body,
                                                       HttpServletRequest request) {
        return ApiResponse.ok(service.validate(id, body), TraceIdFilter.current(request));
    }

    @PutMapping("/{id}/configuration")
    @PreAuthorize("@ss.hasPermi('bi:report:design')")
    @Log(title = "BI报表配置", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    ApiResponse<ReportDtos.Saved> saveConfiguration(@PathVariable long id, @RequestBody ObjectNode body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(service.saveConfiguration(id, body, currentUser.id()), TraceIdFilter.current(request));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("@ss.hasPermi('bi:report:version')")
    ApiResponse<ReportDtos.VersionPage> versions(@PathVariable long id,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                 HttpServletRequest request) {
        return ApiResponse.ok(service.versions(id, page, pageSize), TraceIdFilter.current(request));
    }

    @GetMapping("/{id}/versions/{versionNo}")
    @PreAuthorize("@ss.hasPermi('bi:report:version')")
    ApiResponse<ReportDtos.VersionDetail> version(@PathVariable long id, @PathVariable long versionNo,
                                                  HttpServletRequest request) {
        return ApiResponse.ok(service.version(id, versionNo), TraceIdFilter.current(request));
    }

    @GetMapping("/{id}/versions/{versionNo}/diff")
    @PreAuthorize("@ss.hasPermi('bi:report:version')")
    ApiResponse<ReportDtos.VersionDiff> diff(@PathVariable long id, @PathVariable long versionNo,
                                             @RequestParam long against, HttpServletRequest request) {
        return ApiResponse.ok(service.diff(id, versionNo, against), TraceIdFilter.current(request));
    }

    @PostMapping("/{id}/versions/{versionNo}/rollback")
    @PreAuthorize("@ss.hasPermi('bi:report:version')")
    @Log(title = "BI报表版本回滚", businessType = BusinessType.UPDATE, isSaveResponseData = false)
    ApiResponse<ReportDtos.Saved> rollback(@PathVariable long id, @PathVariable long versionNo,
                                           @Valid @RequestBody ReportDtos.RollbackRequest body,
                                           HttpServletRequest request) {
        return ApiResponse.ok(service.rollback(id, versionNo, body, currentUser.id()), TraceIdFilter.current(request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermi('bi:report:design')")
    @Log(title = "BI报表状态", businessType = BusinessType.UPDATE)
    ApiResponse<Void> changeStatus(@PathVariable long id, @Valid @RequestBody ReportDtos.StatusRequest body,
                                   HttpServletRequest request) {
        service.changeStatus(id, body, currentUser.id());
        return ApiResponse.ok(null, TraceIdFilter.current(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('bi:report:design')")
    @Log(title = "BI报表", businessType = BusinessType.DELETE)
    ApiResponse<Void> delete(@PathVariable long id, HttpServletRequest request) {
        service.delete(id, currentUser.id());
        return ApiResponse.ok(null, TraceIdFilter.current(request));
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("@ss.hasPermi('bi:report:create')")
    @Log(title = "复制BI报表", businessType = BusinessType.INSERT, isSaveResponseData = false)
    ApiResponse<ReportDtos.Created> copy(@PathVariable long id,HttpServletRequest request){return ApiResponse.ok(service.copy(id,currentUser.id()),TraceIdFilter.current(request));}
}
