package cn.xinghuaerp.erp.controller;

import cn.xinghuaerp.inventory.entity.ErpWarehouse;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.system.entity.BusinessUserBinding;
import cn.xinghuaerp.system.entity.ErpMerchant;
import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;
import cn.xinghuaerp.system.service.ISysUserService;
import cn.xinghuaerp.system.service.ErpMerchantService;
import cn.xinghuaerp.system.service.OShopService;
import cn.xinghuaerp.purchase.service.ErpSupplierService;
import cn.xinghuaerp.inventory.service.ErpWarehouseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 仅总部且拥有用户编辑权限的管理员维护主体绑定。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sys-api/system/user/business-binding")
@PreAuthorize("@ss.hasPermi('system:user:edit')")
public class BusinessUserBindingController extends BaseController {
    private final BusinessSubjectScopeService scopes;
    private final ISysUserService users;
    private final ErpMerchantService merchants;
    private final OShopService shops;
    private final ErpSupplierService suppliers;
    private final ErpWarehouseService warehouses;
    public record Option(Long id, String name) {}
    @GetMapping("/options/{userId}")
    public AjaxResult options(@PathVariable Long userId) {
        check(userId);
        var user = users.selectUserById(userId);
        if (user == null) throw new IllegalArgumentException("账号不存在");
        List<Option> options = switch (user.getUserType()) {
            case "20" -> merchants.list(new LambdaQueryWrapper<ErpMerchant>().eq(ErpMerchant::getStatus,"0").eq(ErpMerchant::getDelFlag,"0")).stream().map(x -> new Option(x.getId(), x.getName())).toList();
            case "40" -> shops.list(new LambdaQueryWrapper<OShop>().eq(OShop::getStatus,"1")).stream().map(x -> new Option(x.getId(), x.getName())).toList();
            case "30" -> suppliers.list(new LambdaQueryWrapper<ErpSupplier>().eq(ErpSupplier::getDisable,0).eq(ErpSupplier::getIsDelete,0)).stream().map(x -> new Option(x.getId(), x.getName())).toList();
            case "10" -> warehouses.list(new LambdaQueryWrapper<ErpWarehouse>().eq(ErpWarehouse::getStatus,"1")).stream().map(x -> new Option(x.getId(), x.getWarehouseName())).toList();
            default -> throw new IllegalArgumentException("该账号类型不支持绑定");
        };
        return success(options);
    }
    private void check(Long target) {
        if (!"HEADQUARTERS".equals(scopes.resolve(getUserId()).type())) throw new IllegalArgumentException("仅总部管理员可维护业务主体绑定");
        users.checkUserDataScope(target);
    }
    @GetMapping("/{userId}")
    public AjaxResult get(@PathVariable Long userId) { check(userId); return success(scopes.binding(userId)); }
    @PutMapping
    public AjaxResult bind(@RequestBody BusinessUserBinding binding) {
        if (binding == null) throw new IllegalArgumentException("绑定参数不完整");
        check(binding.getUserId()); scopes.bind(binding.getUserId(),binding.getSubjectType(),binding.getSubjectId(),getUsername()); return success("绑定已保存");
    }
    @DeleteMapping("/{userId}")
    public AjaxResult unbind(@PathVariable Long userId) { check(userId); scopes.unbind(userId,getUsername()); return success("绑定已停用"); }
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException error) { return error(error.getMessage()); }
}
