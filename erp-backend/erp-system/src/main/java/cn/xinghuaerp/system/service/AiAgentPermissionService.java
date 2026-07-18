package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.service.ISysMenuService;
import cn.xinghuaerp.system.security.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Set;

/** 不信任登录缓存中的通配权限；每次从角色菜单读取权限。 */
@Service
@RequiredArgsConstructor
public class AiAgentPermissionService {
    private final ISysMenuService menus;
    public boolean allows(Long userId, String permission) {
        if (userId == null || userId <= 0) return false;
        if (SecurityUtils.isAdmin(userId)) return true;
        Set<String> granted = menus.selectMenuPermsByUserId(userId);
        return granted != null && (granted.contains(permission) || granted.contains("*:*:*"));
    }
}
