package cn.xinghuaerp.inventory.service;

import cn.xinghuaerp.inventory.entity.ErpStockIn;
import cn.xinghuaerp.inventory.entity.ErpStockOut;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.Objects;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;

@Service
@RequiredArgsConstructor
public class WarehouseAccessService {
    private final BusinessAccessService business;
    private final AiAgentPermissionService permissions;
    public Scope scope(Long userId, String permission) {
        if (!permissions.allows(userId, permission)) throw new AccessDeniedException("无仓库操作权限");
        Scope scope = business.scope(userId); validate(scope); return scope;
    }
    public static void validate(Scope s) {
        if (s == null || s.type() == null) throw new AccessDeniedException("无仓库业务范围");
        boolean valid = switch(s.type()) {
            case "HEADQUARTERS" -> true;
            case "MERCHANT" -> s.merchantId() != null && s.merchantId() > 0;
            case "SHOP" -> s.merchantId() != null && s.merchantId() >= 0 && s.shopId() != null && s.shopId() > 0;
            case "WAREHOUSE" -> s.subjectId() != null && s.subjectId() > 0;
            default -> false;
        };
        if (!valid) throw new AccessDeniedException("无仓库业务范围");
    }
    public static boolean visible(Scope s, Long merchant, Long shop, Long warehouse) {
        validate(s);
        return switch(s.type()) {
            case "HEADQUARTERS" -> true;
            case "MERCHANT" -> Objects.equals(s.merchantId(), merchant);
            case "SHOP" -> Objects.equals(s.merchantId(), merchant) && Objects.equals(s.shopId(), shop);
            case "WAREHOUSE" -> Objects.equals(s.subjectId(), warehouse);
            default -> false;
        };
    }
    public static void check(Scope s, ErpStockIn entry) {
        if (entry == null || !visible(s,entry.getMerchantId(),entry.getShopId(),entry.getWarehouseId())) throw new AccessDeniedException("无入库单访问范围");
    }
    public static void check(Scope s, ErpStockOut entry) {
        if (entry == null || !visible(s,entry.getMerchantId(),entry.getShopId(),entry.getWarehouseId())) throw new AccessDeniedException("无出库单访问范围");
    }
    public static void apply(Scope s, ErpStockIn q) {
        validate(s); switch(s.type()) {
            case "MERCHANT" -> q.setMerchantId(s.merchantId());
            case "SHOP" -> {q.setMerchantId(s.merchantId());q.setShopId(s.shopId());}
            case "WAREHOUSE" -> q.setWarehouseId(s.subjectId());
            default -> { }
        }
    }
    public static void apply(Scope s, ErpStockOut q) {
        validate(s); switch(s.type()) {
            case "MERCHANT" -> q.setMerchantId(s.merchantId());
            case "SHOP" -> {q.setMerchantId(s.merchantId());q.setShopId(s.shopId());}
            case "WAREHOUSE" -> q.setWarehouseId(s.subjectId());
            default -> { }
        }
    }
    public static void headquartersOnly(Scope s) {
        validate(s); if (!"HEADQUARTERS".equals(s.type())) throw new AccessDeniedException("手工建仓库单暂仅支持总部");
    }
}
