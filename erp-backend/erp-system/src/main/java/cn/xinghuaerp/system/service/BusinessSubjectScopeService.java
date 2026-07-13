package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.entity.BusinessUserBinding;
import cn.xinghuaerp.system.entity.ErpMerchant;
import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.system.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

import cn.xinghuaerp.system.mapper.ErpMerchantMapper;
import cn.xinghuaerp.system.mapper.OShopMapper;
import cn.xinghuaerp.system.mapper.BusinessUserBindingMapper;
/** 业务主体解析与绑定管理；不以部门 ID 推断归属。 */
@Service
@RequiredArgsConstructor
public class BusinessSubjectScopeService {
    private final BusinessUserBindingMapper bindings;
    private final ErpMerchantMapper merchants;
    private final OShopMapper shops;
    /** 供应商、仓库等主体由各自业务模块提供校验实现，避免系统模块反向依赖。 */
    private final List<BusinessSubjectValidator> validators;

    private void requireSubject(String subjectType, Long subjectId) {
        validators.stream()
                .filter(v -> subjectType.equals(v.subjectType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("业务主体类型无效"))
                .requireActive(subjectId);
    }

    public record Scope(String type, Long subjectId, Long merchantId, Long shopId) {}

    public Scope resolve(Long userId) {
        require(userId != null && userId > 0, "账号身份无效");
        SysUser user = bindings.selectCurrentUser(userId);
        require(user != null && "0".equals(user.getStatus()) && "0".equals(user.getDelFlag()), "账号不存在或已停用");
        String type = user.getUserType();
        if ("00".equals(type) || "0".equals(type)) return new Scope("HEADQUARTERS", null, null, null);
        String expected = switch (type == null ? "" : type) {
            case "20" -> "MERCHANT";
            case "40" -> "SHOP";
            case "30" -> "SUPPLIER";
            case "10" -> "WAREHOUSE";
            default -> throw new IllegalArgumentException("账号类型不支持业务主体绑定");
        };
        BusinessUserBinding binding = bindings.selectById(userId);
        require(binding != null && Integer.valueOf(1).equals(binding.getEnabled()) && expected.equals(binding.getSubjectType())
                && binding.getSubjectId() != null && binding.getSubjectId() > 0, "账号未绑定有效业务主体，请联系管理员");
        return resolveBound(expected, binding.getSubjectId());
    }

    private Scope resolveBound(String expected, Long id) {
        return switch (expected) {
            case "MERCHANT" -> { requireMerchant(id); yield new Scope(expected,id,id,null); }
            case "SHOP" -> {
                OShop shop = shops.selectOne(new LambdaQueryWrapper<OShop>().select(OShop::getId,OShop::getStatus,OShop::getMerchantId).eq(OShop::getId,id));
                require(shop != null && "1".equals(shop.getStatus()) && shop.getMerchantId() != null && shop.getMerchantId() >= 0, "店铺不存在、已停用或缺少商户归属");
                if (shop.getMerchantId() > 0) requireMerchant(shop.getMerchantId());
                yield new Scope(expected,id,shop.getMerchantId(),id);
            }
            case "SUPPLIER" -> {
                requireSubject("SUPPLIER", id);
                yield new Scope(expected,id,null,null);
            }
            case "WAREHOUSE" -> {
                requireSubject("WAREHOUSE", id);
                yield new Scope(expected,id,null,null);
            }
            default -> throw new IllegalArgumentException("业务主体类型无效");
        };
    }
    /** 管理员建立或变更绑定；账号行锁确保并发配置不会产生重复记录。 */
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long userId, String subjectType, Long subjectId, String actor) {
        require(userId != null && userId > 0 && subjectId != null && subjectId > 0 && actor != null && !actor.isBlank(), "绑定参数不完整");
        SysUser user = bindings.lockCurrentUser(userId);
        require(user != null && "0".equals(user.getStatus()) && "0".equals(user.getDelFlag()), "账号不存在或已停用");
        String expected = switch (user.getUserType() == null ? "" : user.getUserType()) {
            case "20" -> "MERCHANT"; case "40" -> "SHOP"; case "30" -> "SUPPLIER"; case "10" -> "WAREHOUSE";
            default -> throw new IllegalArgumentException("系统账号不需要业务主体绑定");
        };
        require(expected.equals(subjectType), "主体类型与用户类型不匹配");
        resolveBound(expected, subjectId);
        BusinessUserBinding current = bindings.selectById(userId); LocalDateTime now = LocalDateTime.now();
        if (current == null) {
            var binding = new BusinessUserBinding(); binding.setUserId(userId); binding.setSubjectType(expected); binding.setSubjectId(subjectId);
            binding.setEnabled(1); binding.setCreateBy(actor); binding.setCreateTime(now); binding.setUpdateBy(actor); binding.setUpdateTime(now);
            require(bindings.insert(binding) == 1, "绑定保存失败");
        } else {
            current.setSubjectType(expected); current.setSubjectId(subjectId); current.setEnabled(1); current.setUpdateBy(actor); current.setUpdateTime(now);
            require(bindings.updateById(current) == 1, "绑定保存失败");
        }
    }
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long userId, String actor) {
        require(userId != null && userId > 0 && actor != null && !actor.isBlank(), "绑定参数不完整");
        require(bindings.lockCurrentUser(userId) != null, "账号不存在");
        BusinessUserBinding binding = bindings.selectById(userId);
        require(binding != null && Integer.valueOf(1).equals(binding.getEnabled()), "账号没有启用的绑定");
        binding.setEnabled(0); binding.setUpdateBy(actor); binding.setUpdateTime(LocalDateTime.now());
        require(bindings.updateById(binding) == 1, "解绑失败");
    }
    public BusinessUserBinding binding(Long userId) {
        require(userId != null && userId > 0 && bindings.selectCurrentUser(userId) != null, "账号不存在");
        return bindings.selectById(userId);
    }

    private void requireMerchant(Long id) {
        ErpMerchant merchant = merchants.selectOne(new LambdaQueryWrapper<ErpMerchant>().select(ErpMerchant::getId,ErpMerchant::getStatus,ErpMerchant::getDelFlag).eq(ErpMerchant::getId,id));
        require(merchant != null && "0".equals(merchant.getStatus()) && "0".equals(merchant.getDelFlag()), "商户不存在或已停用");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
