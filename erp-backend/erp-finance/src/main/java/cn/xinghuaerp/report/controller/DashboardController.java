package cn.xinghuaerp.report.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.report.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/dashboard")
public class DashboardController extends BaseController {
    private final DashboardService service;
    private final cn.xinghuaerp.system.service.BusinessAccessService access;
    public record Scope(Long merchantId,Long shopId,String label) {}
    private Scope resolveScope() {
        return resolveScope(access.scope(getUserId()));
    }
    static Scope resolveScope(cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope subject) {
        return switch (subject.type()) {
            case "HEADQUARTERS" -> new Scope(null,null,"全部商户 · 全部店铺");
            case "MERCHANT" -> new Scope(subject.merchantId(),null,"当前商户 · 全部店铺");
            case "SHOP" -> new Scope(subject.merchantId(),subject.shopId(),"当前店铺 · 店铺独立库存");
            default -> throw new IllegalArgumentException("当前账号暂不支持经营看板");
        };
    }
    @GetMapping("/overview")
    public AjaxResult overview(@RequestParam(defaultValue="7") int days) {
        Scope scope=resolveScope();
        return success(service.overview(days,scope.merchantId(),scope.shopId(),scope.label()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult invalid(IllegalArgumentException error){return error(error.getMessage());}
}
