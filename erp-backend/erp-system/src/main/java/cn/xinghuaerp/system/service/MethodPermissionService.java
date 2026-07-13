package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 方法权限使用当前账号的实时角色菜单，不依赖登录时缓存。 */
@Component("ss")
@RequiredArgsConstructor
public class MethodPermissionService {
    private final AiAgentPermissionService permissions;

    public boolean hasPermi(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!StringUtils.hasText(permission) || authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser user)) return false;
        return permissions.allows(user.getUserId(), permission);
    }
}
