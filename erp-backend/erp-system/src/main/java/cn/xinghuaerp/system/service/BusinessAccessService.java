package cn.xinghuaerp.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** 使用数据库中的业务主体绑定，而不是登录缓存的部门ID。 */
@Service
@RequiredArgsConstructor
public class BusinessAccessService {
    private final BusinessSubjectScopeService scopes;
    public SalesScope salesScope(Long userId) {
        var subject = scopes.resolve(userId);
        return switch (subject.type()) {
            case "HEADQUARTERS" -> new SalesScope(null, null);
            case "MERCHANT" -> new SalesScope(subject.merchantId(), null);
            case "SHOP" -> new SalesScope(subject.merchantId(), subject.shopId());
            default -> throw new IllegalArgumentException("当前账号没有销售和售后访问范围");
        };
    }
    public void requireHeadquarters(Long userId) {
        var subject = scope(userId);
        if (subject == null || !"HEADQUARTERS".equals(subject.type()))
            throw new AccessDeniedException("订单管理暂仅支持总部账号");
    }
    public BusinessSubjectScopeService.Scope scope(Long userId) { return scopes.resolve(userId); }
}
