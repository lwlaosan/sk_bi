package com.ruoyi.bi.report;

import com.ruoyi.bi.api.BiException;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportAccess {
    public boolean isAdmin() { return SecurityUtils.isAdmin(); }
    public long userId() { return SecurityUtils.getUserId(); }

    public List<Long> roleIds() {
        List<SysRole> roles = SecurityUtils.getLoginUser().getUser().getRoles();
        if (roles == null) return List.of();
        return roles.stream().map(SysRole::getRoleId).filter(id -> id != null).distinct().toList();
    }

    public void requireReadable(long reportId, ReportRepository repository) {
        if (!isAdmin() && !repository.hasManagementAccess(reportId, userId(), roleIds())) {
            throw new BiException(HttpStatus.NOT_FOUND, "BI_REQUEST_INVALID", "报表不存在");
        }
    }
}
