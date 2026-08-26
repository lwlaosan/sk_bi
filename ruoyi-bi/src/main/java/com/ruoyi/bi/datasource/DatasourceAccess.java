package com.ruoyi.bi.datasource;

import com.ruoyi.bi.api.BiException;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatasourceAccess {
    public boolean isAdmin() {
        return SecurityUtils.isAdmin();
    }

    public long userId() {
        return SecurityUtils.getUserId();
    }

    public List<Long> roleIds() {
        List<SysRole> roles = SecurityUtils.getLoginUser().getUser().getRoles();
        if (roles == null) return List.of();
        return roles.stream().map(SysRole::getRoleId).filter(id -> id != null).distinct().toList();
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new BiException(HttpStatus.FORBIDDEN, "BI_DATASOURCE_FORBIDDEN", "仅系统管理员可以维护或测试数据源");
        }
    }

    public void requireReadable(long datasourceId, DatasourceRepository repository) {
        if (!isAdmin() && !repository.hasAccess(datasourceId, userId(), roleIds())) {
            throw new BiException(HttpStatus.FORBIDDEN, "BI_DATASOURCE_FORBIDDEN", "无权访问该数据源");
        }
    }
}
