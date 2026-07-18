package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.entity.SysUser;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.system.security.UserDetailsServiceImpl;
import cn.xinghuaerp.system.service.SysPermissionService;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginPermissionTest {
    @Test void newLoginsUseRolePermissionsInsteadOfUniversalAccess() throws Exception {
        var provider = new UserDetailsServiceImpl(); var permissions = mock(SysPermissionService.class);
        var field = UserDetailsServiceImpl.class.getDeclaredField("permissionService"); field.setAccessible(true); field.set(provider,permissions);
        var user = new SysUser(); user.setUserId(7L);
        when(permissions.getMenuPermission(user)).thenReturn(Set.of("ai:inventory:query"));
        var login = (LoginUser)provider.createLoginUser(user);
        assertEquals(Set.of("ai:inventory:query"), login.getPermissions());
        assertFalse(login.getPermissions().contains("*:*:*"));
    }
}
