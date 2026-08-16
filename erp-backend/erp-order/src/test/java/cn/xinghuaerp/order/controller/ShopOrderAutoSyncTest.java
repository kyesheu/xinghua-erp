package cn.xinghuaerp.order.controller;

import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.order.service.impl.ShopSyncService;
import cn.xinghuaerp.order.bo.ShopOrderCreateBo;
import cn.xinghuaerp.order.service.OOrderService;
import cn.xinghuaerp.order.service.ShopOrderService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.order.controller.ShopOrderController;

/** Creation/sync response contract with isolated service doubles; never creates real orders. */
class ShopOrderAutoSyncTest {
    @Test
    void automaticallySyncsTheCreatedOrderAndKeepsCreatedIdInResponse() {
        var calls = new AtomicInteger();
        var controller = controller(ResultVo.success(101L), id -> {
            calls.incrementAndGet();
            assertEquals(101L, id);
            return ResultVo.success();
        });
        var response = controller.create(request());
        assertEquals(200, response.get("code"));
        assertEquals(101L, response.get("data"));
        assertEquals(true, response.get("omsSynced"));
        assertTrue(response.get("msg").toString().contains("同步到订单库"));
        assertEquals(1, calls.get());
    }

    @Test
    void creationFailureDoesNotTrySync() {
        var controller = controller(ResultVo.error("订单号已存在"), id -> fail("创建失败不能同步"));
        var response = controller.create(request());
        assertNotEquals(200, response.get("code"));
        assertEquals("订单号已存在", response.get("msg"));
        assertFalse(response.containsKey("omsSynced"));
    }

    @Test
    void syncFailureStillReportsCreatedOrderAndExplainsRetryWithoutRecreation() {
        var response = controller(ResultVo.success(101L), id -> ResultVo.error("同步失败"))
                .create(request());
        assertEquals(200, response.get("code"));
        assertEquals(101L, response.get("data"));
        assertEquals(false, response.get("omsSynced"));
        assertTrue(response.get("msg").toString().contains("请勿重复创建"));
        assertTrue(response.get("msg").toString().contains("重新推送"));
    }

    @Test
    void syncExceptionDoesNotTurnSuccessfulCreationIntoAnError() {
        var response = controller(ResultVo.success(101L), id -> {
            throw new IllegalStateException("isolated sync failure");
        }).create(request());
        assertEquals(200, response.get("code"));
        assertEquals(101L, response.get("data"));
        assertEquals(false, response.get("omsSynced"));
    }

    @Test
    void missingShopDoesNotCreateOrSyncAnything() {
        var controller = controller(null, id -> fail("参数无效不能同步"));
        assertNotEquals(200, controller.create(new ShopOrderCreateBo()).get("code"));
    }

    @Test
    void manualRetryReportsPartialFailures() {
        var controller = controller(null, id -> id == 101L ? ResultVo.success() : ResultVo.error("失败"));
        var response = controller.pushOms(java.util.Map.of("ids", java.util.List.of(101L, 102L)));
        assertEquals(1, response.get("successCount"));
        assertEquals(1, response.get("failCount"));
    }

    private ShopOrderController controller(ResultVo<Long> created, Function<Long, ResultVo<Long>> sync) {
        var shopOrders = (ShopOrderService) Proxy.newProxyInstance(ShopOrderService.class.getClassLoader(),
                new Class<?>[]{ShopOrderService.class}, (proxy, method, args) -> {
                    assertEquals("createOrder", method.getName());
                    assertNotNull(created, "参数无效时不应该创建订单");
                    assertEquals("tester", args[1]);
                    return created;
                });
        var orders = (OOrderService) Proxy.newProxyInstance(OOrderService.class.getClassLoader(),
                new Class<?>[]{OOrderService.class}, (proxy, method, args) -> {
                    assertEquals("shopOrderMessage", method.getName());
                    return sync.apply((Long) args[0]);
                });
        return new ShopOrderController(shopOrders, null, new ShopSyncService(orders, null), null) {
            @Override public String getUsername() { return "tester"; }
        };
    }

    private ShopOrderCreateBo request() {
        var request = new ShopOrderCreateBo();
        request.setShopId(10L);
        return request;
    }
}
