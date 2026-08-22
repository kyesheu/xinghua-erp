package cn.xinghuaerp.report.controller;

import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.report.controller.DashboardController;

class DashboardControllerTest {
    @Test void mapsResolvedBusinessScope() {
        var h = DashboardController.resolveScope(new Scope("HEADQUARTERS", null, null, null));
        assertNull(h.merchantId()); assertNull(h.shopId());
        var m = DashboardController.resolveScope(new Scope("MERCHANT", 7L, 7L, null));
        assertEquals(7L, m.merchantId()); assertNull(m.shopId());
        var s = DashboardController.resolveScope(new Scope("SHOP", 10L, 7L, 10L));
        assertEquals(7L, s.merchantId()); assertEquals(10L, s.shopId());
        assertThrows(IllegalArgumentException.class, () -> DashboardController.resolveScope(new Scope("SUPPLIER", 20L, null, null)));
    }
}
