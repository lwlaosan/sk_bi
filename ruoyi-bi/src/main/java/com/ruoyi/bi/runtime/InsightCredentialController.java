package com.ruoyi.bi.runtime;

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
@RequestMapping("/api/bi/admin/insight/providers")
public class InsightCredentialController {
    private final InsightCredentialService service;private final CurrentUser user;
    public InsightCredentialController(InsightCredentialService service,CurrentUser user){this.service=service;this.user=user;}
    @GetMapping
    @PreAuthorize("@ss.hasPermi('bi:datasource:manage')")
    ApiResponse<?> statuses(HttpServletRequest request){return ApiResponse.ok(service.statuses(),TraceIdFilter.current(request));}
    @PutMapping("/{provider}/credential")
    @PreAuthorize("@ss.hasPermi('bi:datasource:manage')")
    @Log(title="BI模型密钥",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    ApiResponse<?> save(@PathVariable String provider,@Valid @RequestBody InsightCredentialService.SaveRequest body,HttpServletRequest request){return ApiResponse.ok(service.save(provider,body,user.id()),TraceIdFilter.current(request));}
    @DeleteMapping("/{provider}/credential")
    @PreAuthorize("@ss.hasPermi('bi:datasource:manage')")
    @Log(title="BI模型密钥",businessType=BusinessType.DELETE,isSaveRequestData=false,isSaveResponseData=false)
    ApiResponse<Void> delete(@PathVariable String provider,HttpServletRequest request){service.delete(provider);return ApiResponse.ok(null,TraceIdFilter.current(request));}
}
