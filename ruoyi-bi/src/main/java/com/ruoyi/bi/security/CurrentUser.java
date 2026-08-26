package com.ruoyi.bi.security;

import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public long id() {
        return SecurityUtils.getUserId();
    }
}
