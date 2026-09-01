package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.ai.service.AiInventoryReadService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.controller.InventoryAgentController;

class InventoryAgentControllerTest {
    final BusinessAccessService access = mock(BusinessAccessService.class);
    final AiInventoryReadService inventory = mock(AiInventoryReadService.class);
    final PythonAgentClient python = mock(PythonAgentClient.class);
    final AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
    final InventoryAgentController controller = new InventoryAgentController(access, inventory, python, permissions);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void login(Set<String> permissions) {
        var user = new LoginUser(); user.setUserId(7L); user.setPermissions(Set.of("*:*:*"));
        for (String permission : permissions) when(this.permissions.allows(7L, permission)).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
    @Test void browserStringIdentifiersAreParsedWithoutJavascriptPrecisionLoss() {
        var request = JsonUtils.readValue("{\"skuId\":\"9007199254740993\",\"warehouseId\":\"1\",\"days\":30}", InventoryAgentController.Request.class);
        assertEquals(9007199254740993L, request.skuId());
        assertEquals(1L, request.warehouseId());
    }
    @Test void withoutInventoryPermissionNoDataOrModelIsCalled() {
        login(Set.of("ai:purchase:query"));
        assertThrows(AccessDeniedException.class, () -> controller.analyze(new InventoryAgentController.Request(5L,null,30,null,null,null,null)));
        verifyNoInteractions(access, inventory, python);
    }
    @Test void inventoryOnlyDoesNotLoadPurchaseAndUsesResolvedScope() {
        login(Set.of("ai:inventory:query"));
        var scope = new Scope("SHOP",10L,7L,10L);
        when(access.scope(7L)).thenReturn(scope);
        when(inventory.analysisSnapshot(5L,1L,30,scope,false,false)).thenReturn(new AiInventoryReadService.AnalysisSnapshot(LocalDateTime.now(),new AiInventoryReadService.Snapshot(List.of(),"店铺","库存限制"),null,null,List.of()));
        controller.analyze(new InventoryAgentController.Request(5L,1L,30,null,null,null,null));
        verify(inventory).analysisSnapshot(5L,1L,30,scope,false,false);
        verify(python).analyze(argThat(value -> value instanceof InventoryAgentController.Facts facts && facts.purchase() == null));
    }
    @Test void incompleteManualAssumptionsAreRejected() {
        login(Set.of("ai:inventory:query"));
        assertThrows(IllegalArgumentException.class, () -> controller.analyze(new InventoryAgentController.Request(5L,null,30,new BigDecimal("2"),30,0L,null)));
        assertThrows(IllegalArgumentException.class, () -> controller.analyze(new InventoryAgentController.Request(5L,1L,30,new BigDecimal("0"),30,0L,null)));
        verifyNoInteractions(access, inventory, python);
    }
    @Test void arrivalAndLeadTimeAreValidatedBeforeReadingBusinessData() {
        var invalid = JsonUtils.readValue("{\"skuId\":5,\"warehouseId\":1,\"days\":30,\"dailyDemand\":3,\"targetDays\":10,\"confirmedIncoming\":20,\"incomingArrivalDays\":-1}", InventoryAgentController.Request.class);
        assertThrows(IllegalArgumentException.class, () -> controller.analyze(invalid));
        verifyNoInteractions(access, inventory, python);
    }
    @Test void batchTotalMismatchIsRejectedBeforeReadingBusinessData() {
        var invalid = JsonUtils.readValue("{\"skuId\":5,\"warehouseId\":1,\"days\":30,\"dailyDemand\":3,\"targetDays\":10,\"confirmedIncoming\":20,\"incomingBatches\":[{\"quantity\":19,\"arrivalDays\":2}]}", InventoryAgentController.Request.class);
        assertThrows(IllegalArgumentException.class, () -> controller.analyze(invalid));
        verifyNoInteractions(access, inventory, python);
    }
    @Test void purchasePermissionAllowsScopedPurchase() {
        login(Set.of("ai:inventory:query", "ai:sales:query", "ai:purchase:query"));
        var scope = new Scope("MERCHANT",7L,7L,null);
        when(access.scope(7L)).thenReturn(scope);
        var sales = new AiInventoryReadService.Sales(5,0,LocalDateTime.now(),LocalDateTime.now(),"商户","限制");
        var purchase = new AiInventoryReadService.PurchaseInbound(List.of(),List.of(),"商户","限制");
        when(inventory.analysisSnapshot(5L,null,7,scope,true,true)).thenReturn(new AiInventoryReadService.AnalysisSnapshot(LocalDateTime.now(),new AiInventoryReadService.Snapshot(List.of(),"商户","限制"),sales,purchase,List.of()));
        controller.analyze(new InventoryAgentController.Request(5L,null,7,null,null,null,null));
        verify(inventory).analysisSnapshot(5L,null,7,scope,true,true);
    }
}
