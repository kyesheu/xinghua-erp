package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.ai.service.ToolToken;
import cn.xinghuaerp.ai.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 工具网关是 Java 侧唯一对 AI 服务开放的数据入口，必须挡住非本机、错密钥、无令牌的调用。 */
class AiToolGatewayTest {

    private static final String SECRET = "s".repeat(32);

    private final PythonAgentClient python = mock(PythonAgentClient.class);
    private final ToolToken tokens = new ToolToken("t".repeat(32));
    private final GoodsTools goodsTools = mock(GoodsTools.class);
    private final OrderTools orderTools = mock(OrderTools.class);
    private final RefundTools refundTools = mock(RefundTools.class);
    private final InventoryTools inventoryTools = mock(InventoryTools.class);
    private final ShopTools shopTools = mock(ShopTools.class);
    private final PurchaseTools purchaseTools = mock(PurchaseTools.class);
    private final MemberTools memberTools = mock(MemberTools.class);
    private final SupplierTools supplierTools = mock(SupplierTools.class);
    private final LogisticsTools logisticsTools = mock(LogisticsTools.class);
    private final WarehouseTools warehouseTools = mock(WarehouseTools.class);
    private final StockFlowTools stockFlowTools = mock(StockFlowTools.class);

    private final cn.xinghuaerp.system.service.BusinessAccessService access = mock(cn.xinghuaerp.system.service.BusinessAccessService.class);
    private final cn.xinghuaerp.system.service.AiAgentPermissionService permissions = mock(cn.xinghuaerp.system.service.AiAgentPermissionService.class);
    private final cn.xinghuaerp.ai.service.AiInventoryReadService scopedInventory = mock(cn.xinghuaerp.ai.service.AiInventoryReadService.class);
    private final cn.xinghuaerp.system.service.SalesScope salesScope = new cn.xinghuaerp.system.service.SalesScope(7L, 10L);
    private final cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope subject = new cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope("SHOP",10L,7L,10L);
    private AiToolGateway gateway;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(python.agentSecret()).thenReturn(SECRET);
        when(permissions.allows(anyLong(), anyString())).thenReturn(true);
        when(access.scope(anyLong())).thenReturn(subject);
        when(access.salesScope(anyLong())).thenReturn(salesScope);
        gateway = new AiToolGateway(python, tokens, access, permissions, scopedInventory, goodsTools, orderTools, refundTools, inventoryTools, shopTools,
                purchaseTools, memberTools, supplierTools, logisticsTools, warehouseTools, stockFlowTools);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void rejectsNonLoopbackCaller() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.8");
        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.query(new AiToolGateway.ToolQuery("shop.list", Map.of()), SECRET, tokens.issue(1L), request));
        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void rejectsWrongSecret() {
        var error = assertThrows(ResponseStatusException.class,
                () -> gateway.query(new AiToolGateway.ToolQuery("shop.list", Map.of()), "x".repeat(32), tokens.issue(1L), request));
        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void rejectsMissingOrForgedToken() {
        assertThrows(ResponseStatusException.class,
                () -> gateway.query(new AiToolGateway.ToolQuery("shop.list", Map.of()), SECRET, "", request));
        assertThrows(ResponseStatusException.class,
                () -> gateway.query(new AiToolGateway.ToolQuery("shop.list", Map.of()), SECRET, "1.9999999999999.forged", request));
    }

    @Test
    void dispatchesByResourceAndClampsLimit() {
        when(orderTools.getOrderList(7, null, 100, salesScope)).thenReturn("订单表");
        var result = gateway.query(new AiToolGateway.ToolQuery("order.list", Map.of("days", "7")), SECRET, tokens.issue(9L), request);
        assertEquals("order.list", result.get("resource"));
        assertEquals("订单表", result.get("text"));

        when(inventoryTools.getInventoryList(5, 500, subject)).thenReturn("库存表");
        assertEquals("库存表", gateway.query(new AiToolGateway.ToolQuery("inventory.list",
                Map.of("availableLte", 5, "limit", 99999)), SECRET, tokens.issue(9L), request).get("text"));
    }

    @Test
    void rejectsUnknownResourceAndBlankResource() {
        assertThrows(ResponseStatusException.class,
                () -> gateway.query(new AiToolGateway.ToolQuery("order.delete", Map.of()), SECRET, tokens.issue(1L), request));
        assertThrows(ResponseStatusException.class,
                () -> gateway.query(new AiToolGateway.ToolQuery(" ", Map.of()), SECRET, tokens.issue(1L), request));
    }

    @Test
    void rejectsTooManyParameters() {
        var params = new java.util.HashMap<String, Object>();
        for (int i = 0; i < 11; i++) params.put("p" + i, i);
        assertThrows(ResponseStatusException.class,
                () -> gateway.query(new AiToolGateway.ToolQuery("shop.list", params), SECRET, tokens.issue(1L), request));
    }
    @Test void permissionAndSubjectAreCheckedBeforeAnyGlobalTool() {
        when(permissions.allows(9L,"ai:sales:query")).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> gateway.query(new AiToolGateway.ToolQuery("order.list",Map.of()),SECRET,tokens.issue(9L),request));
        assertThrows(ResponseStatusException.class, () -> gateway.query(new AiToolGateway.ToolQuery("purchase.list",Map.of()),SECRET,tokens.issue(9L),request));
        verifyNoInteractions(orderTools, purchaseTools);
    }
}
