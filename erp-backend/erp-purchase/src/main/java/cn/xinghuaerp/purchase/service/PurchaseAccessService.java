package cn.xinghuaerp.purchase.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;

/** 旧采购流程尚未完整支持主体隔离，仅开放给有相应权限的总部账号。 */
@Service
@RequiredArgsConstructor
public class PurchaseAccessService {
    private final BusinessAccessService business;
    private final AiAgentPermissionService permissions;

    public void requireHeadquarters(Long userId, String permission) {
        if (!permissions.allows(userId, permission)) throw new AccessDeniedException("无采购操作权限");
        requireHeadquartersScope(userId);
    }

    /**
     * 多个权限任一通过即可（用于读接口同时接受“查询”与“维护”权限），仍要求总部账号。
     *
     * <p>校验顺序与 {@link #requireHeadquarters(Long, String)} 保持一致：先校验实时权限，
     * 再校验业务主体为总部；任一步失败都抛 {@link AccessDeniedException}。</p>
     *
     * @param userId          登录用户ID
     * @param permissionCodes 允许访问的权限标识集合，至少需要一个；null 元素会被忽略
     */
    public void requireHeadquartersAny(Long userId, String... permissionCodes) {
        boolean allowed = false;
        if (permissionCodes != null) {
            for (String code : permissionCodes) {
                if (code != null && permissions.allows(userId, code)) {
                    allowed = true;
                    break;
                }
            }
        }
        if (!allowed) throw new AccessDeniedException("无采购操作权限");
        requireHeadquartersScope(userId);
    }

    /**
     * 校验业务主体为总部。
     *
     * @param userId 登录用户ID
     */
    private void requireHeadquartersScope(Long userId) {
        var scope = business.scope(userId);
        if (scope == null || !"HEADQUARTERS".equals(scope.type()))
            throw new AccessDeniedException("采购管理暂仅支持总部账号");
    }
}
