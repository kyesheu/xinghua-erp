package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.system.entity.ErpMerchant;
import cn.xinghuaerp.system.service.ErpMerchantService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.system.controller.MerchantController;

/** Controller contract tests with a service double; no real merchant writes. */
class MerchantControllerTest {
    @Test
    void managementListHasNoSyntheticHeadquartersAndKeepsDisabledRows() {
        var disabled = new ErpMerchant();
        disabled.setId(12L);
        disabled.setStatus("1");
        var page = PageResult.build(new ArrayList<>(List.of(disabled)));
        var controller = controller((name, args) -> {
            assertEquals("queryPageList", name);
            return page;
        });
        var result = controller.pageList(null, null);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRows().size());
        assertSame(disabled, result.getRows().get(0));
    }

    @Test
    void detailsReturnDataAndMissingOrVirtualMerchantIsAnError() {
        var merchant = new ErpMerchant();
        merchant.setId(12L);
        merchant.setName("测试商户");
        merchant.setStatus("1");
        var controller = controller((name, args) -> (Long) args[0] == 12L ? merchant : null);
        assertSame(merchant, controller.getInfo(12L).get("data"));
        assertNotEquals(200, controller.getInfo(0L).get("code"));
        assertNotEquals(200, controller.getInfo(999L).get("code"));
    }

    @Test
    void editUsesRealUpdateResultAndSuppliesOperator() {
        var merchant = new ErpMerchant();
        merchant.setId(12L);
        merchant.setStatus("0");
        var controller = controller((name, args) -> {
            assertEquals("updateById", name);
            var update = (ErpMerchant) args[0];
            assertEquals("tester", update.getUpdateBy());
            assertNotNull(update.getUpdateTime());
            assertEquals("0", update.getStatus());
            return true;
        });
        assertEquals(200, controller.edit(merchant).get("code"));
        assertNotEquals(200, controller((name, args) -> false).edit(merchant).get("code"));
        merchant.setStatus("invalid");
        assertNotEquals(200, controller.edit(merchant).get("code"));
        merchant.setId(0L);
        assertNotEquals(200, controller.edit(merchant).get("code"));
    }

    private MerchantController controller(ServiceCall call) {
        var service = (ErpMerchantService) Proxy.newProxyInstance(ErpMerchantService.class.getClassLoader(),
                new Class<?>[]{ErpMerchantService.class}, (proxy, method, args) -> call.invoke(method.getName(), args));
        return new MerchantController(service) {
            @Override public String getUsername() { return "tester"; }
        };
    }

    private interface ServiceCall { Object invoke(String name, Object[] args); }
}
