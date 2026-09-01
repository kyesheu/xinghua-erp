package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.xinghuaerp.ai.service.AiFinanceReadService;
import cn.xinghuaerp.ai.service.AiInventoryReadService;
import cn.xinghuaerp.ai.service.AiRiskSummaryService;
import cn.xinghuaerp.ai.service.AiSalesReadService;
import cn.xinghuaerp.ai.controller.ManagementAgentController;
import cn.xinghuaerp.ai.vo.AiInventoryOverviewRow;
class ManagementAgentControllerTest {
    final AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
    final BusinessAccessService access = mock(BusinessAccessService.class);
    final AiSalesReadService sales = mock(AiSalesReadService.class);
    final AiFinanceReadService finance = mock(AiFinanceReadService.class);
    final PythonAgentClient python = mock(PythonAgentClient.class);
    final AiInventoryReadService inventory = mock(AiInventoryReadService.class);
    final AiRiskSummaryService risk = mock(AiRiskSummaryService.class);
    final ManagementAgentController controller = new ManagementAgentController(permissions, access, sales, finance, risk, inventory, python);
    @BeforeEach void login() {
        var user = new LoginUser(); user.setUserId(7L); user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    /** skuPerformance 摘要样例：供 SalesFacts.sku 裁剪填充断言。 */
    private AiSalesReadService.SkuPerformance skuPerf(long skuCount) {
        var now = LocalDateTime.now();
        return new AiSalesReadService.SkuPerformance(7, now, now, now, skuCount, 0, BigDecimal.TEN, null,
                java.util.Map.of("top5", 500, "top10", 800, "top20", 1000),
                java.util.Map.of("highSalesHighRefund", 1, "lowSalesLowRefund", 2),
                java.util.Map.of("highSalesHighRefund", new BigDecimal("90")),
                List.of(), "SKU限制");
    }

    @Test void noPermissionDoesNotReadOrSendFacts() {
        assertThrows(AccessDeniedException.class, () -> controller.analyze(7, "both", null));
        verifyNoInteractions(access, sales, finance, risk, python);
    }
    @Test void salesOnlyUsesServerScopeAndDoesNotReadFinance() {
        when(permissions.allows(7L, "ai:sales:query")).thenReturn(true);
        var scope = new Scope("SHOP", 10L, 7L, 10L);
        when(access.scope(7L)).thenReturn(scope);
        var metrics = new AiSalesReadService.Metrics(5, 3, new BigDecimal("12.50"));
        var now = LocalDateTime.now();
        when(sales.overview(7, scope)).thenReturn(new AiSalesReadService.Overview("绑定店铺", 7, now, now, now, metrics, metrics, List.of(), List.of(), List.of(), List.of(), new AiSalesReadService.Quality(0, 0), "未扣退款"));
        when(sales.skuPerformance(7, scope, 100)).thenReturn(skuPerf(0));
        controller.analyze(7, "both", null);
        verifyNoInteractions(finance);
        verify(sales).skuPerformance(7, scope, 100);
        verify(python).managementAnalysis(argThat(value -> value instanceof ManagementAgentController.Facts facts && facts.finance() == null && facts.sales().orders() == 5 && facts.sales().previousPayment().compareTo(new BigDecimal("12.50")) == 0 && facts.scope().equals("绑定店铺")));
    }
    @Test void invalidWindowDoesNotReadData() {
        assertThrows(IllegalArgumentException.class, () -> controller.analyze(8, "both", null));
        verifyNoInteractions(access, sales, finance, risk, python);
    }
    @Test void selectedSalesDoesNotRequestFinanceEvenWhenAuthorized() {
        when(permissions.allows(7L, "ai:sales:query")).thenReturn(true);
        var scope = new Scope("SHOP", 10L, 7L, 10L);
        when(access.scope(7L)).thenReturn(scope);
        var metrics = new AiSalesReadService.Metrics(5, 3, BigDecimal.TEN);
        var now = LocalDateTime.now();
        when(sales.overview(7, scope)).thenReturn(new AiSalesReadService.Overview("绑定店铺", 7, now, now, now, metrics, metrics, List.of(), List.of(), List.of(), List.of(), new AiSalesReadService.Quality(0, 0), "限制"));
        when(sales.skuPerformance(7, scope, 100)).thenReturn(skuPerf(0));
        controller.analyze(7, "sales", new ManagementAgentController.Request("  分析限制  ", true));
        verifyNoInteractions(finance);
        verify(permissions, never()).allows(7L, "ai:finance:query");
        verify(python).managementAnalysis(argThat(value -> value instanceof ManagementAgentController.Facts facts && facts.useModel() && facts.question().equals("分析限制")));
    }
    @Test void riskOnlyUsesServerScopeAndDoesNotReadOtherSources() {
        when(permissions.allows(7L, "ai:risk:query")).thenReturn(true);
        var scope = new Scope("SHOP", 10L, 7L, 10L);
        when(access.scope(7L)).thenReturn(scope);
        when(risk.overview(7, scope)).thenReturn(new AiRiskSummaryService.Overview("绑定店铺", 2, "仅供核对"));
        controller.analyze(7, "risk", null);
        verifyNoInteractions(sales, finance);
        verify(python).managementAnalysis(argThat(value -> value instanceof ManagementAgentController.Facts facts && facts.risk().refunds() == 2 && facts.sales() == null && facts.finance() == null));
    }
    @Test void inventorySummaryUsesScopedAllSku() {
        when(permissions.allows(7L, "ai:inventory:query")).thenReturn(true);
        var scope = new Scope("SHOP", 10L, 7L, 10L);
        when(access.scope(7L)).thenReturn(scope);
        var row = new cn.xinghuaerp.ai.vo.AiInventoryOverviewRow(); row.setRisk("缺货");
        when(inventory.riskSummary(scope)).thenReturn(new AiInventoryReadService.RiskSummary(120,"绑定店铺","需核实",java.util.Map.of("缺货",1L)));
        controller.analyze(7,"inventory",null);
        verifyNoInteractions(sales,finance,risk);
        verify(python).managementAnalysis(argThat(value -> value instanceof ManagementAgentController.Facts facts && facts.inventory().total()==120 && facts.inventory().scanned()==120 && facts.inventory().risks().get("缺货")==1 && facts.inventory().limitation().contains("全部SKU")));
    }
    @Test void automaticPlanChecksEachSelectedPermissionBeforeReading() {
        when(permissions.allows(7L, "ai:sales:query")).thenReturn(true);
        when(python.managementPlan(any())).thenReturn(JsonUtils.readTree("{\"status\":\"ok\",\"topics\":[\"sales\",\"risk\"]}"));
        assertThrows(AccessDeniedException.class, () -> controller.analyze(7, "auto", new ManagementAgentController.Request("销售和售后", false)));
        verifyNoInteractions(access, sales, finance, risk);
        verify(python, never()).managementAnalysis(any());
    }
    @Test void automaticInventoryRequiresInventoryPermissionEvenWithSalesPermission() {
        when(permissions.allows(7L,"ai:sales:query")).thenReturn(true);
        when(python.managementPlan(any())).thenReturn(JsonUtils.readTree("{\"status\":\"ok\",\"topics\":[\"sales\",\"inventory\"]}"));
        assertThrows(AccessDeniedException.class, () -> controller.analyze(7,"auto",new ManagementAgentController.Request("销售和库存",false)));
        verifyNoInteractions(access,sales,finance,risk,inventory);
        verify(python,never()).managementAnalysis(any());
    }
    @Test void automaticInventoryOnlyUsesAuthorizedScope() {
        when(permissions.allows(7L,"ai:inventory:query")).thenReturn(true);
        when(python.managementPlan(any())).thenReturn(JsonUtils.readTree("{\"status\":\"ok\",\"topics\":[\"inventory\"]}"));
        var scope = new Scope("SHOP",10L,7L,10L);
        when(access.scope(7L)).thenReturn(scope);
        when(inventory.riskSummary(scope)).thenReturn(new AiInventoryReadService.RiskSummary(0,"绑定店铺","限制",java.util.Map.of()));
        controller.analyze(7,"auto",new ManagementAgentController.Request("当前库存",false));
        verify(python).managementAnalysis(argThat(value -> value instanceof ManagementAgentController.Facts facts && facts.inventory()!=null && facts.sales()==null));
    }
    @Test void automaticPlanRoutesOnlySelectedAuthorizedSource() {
        when(permissions.allows(7L, "ai:risk:query")).thenReturn(true);
        when(python.managementPlan(any())).thenReturn(JsonUtils.readTree("{\"status\":\"ok\",\"topics\":[\"risk\"],\"days\":7}"));
        var scope = new Scope("SHOP", 10L, 7L, 10L);
        when(access.scope(7L)).thenReturn(scope);
        when(risk.overview(7, scope)).thenReturn(new AiRiskSummaryService.Overview("绑定店铺", 2, "仅供核对"));
        controller.analyze(30, "auto", new ManagementAgentController.Request("近7天售后情况", false));
        verifyNoInteractions(sales, finance);
        verify(python).managementAnalysis(argThat(value -> value instanceof ManagementAgentController.Facts facts && facts.risk().refunds() == 2 && !facts.useModel()));
    }
    @Test void unsupportedPlanDoesNotReadBusinessData() {
        when(permissions.allows(7L, "ai:risk:query")).thenReturn(true);
        when(python.managementPlan(any())).thenReturn(JsonUtils.readTree("{\"status\":\"unsupported\",\"topics\":[]}"));
        assertThrows(IllegalStateException.class, () -> controller.analyze(7, "auto", new ManagementAgentController.Request("处理退款", false)));
        verifyNoInteractions(access, sales, finance, risk);
        verify(python, never()).managementAnalysis(any());
    }
    @Test void invalidPlanWindowDoesNotReadBusinessData() {
        when(permissions.allows(7L, "ai:risk:query")).thenReturn(true);
        when(python.managementPlan(any())).thenReturn(JsonUtils.readTree("{\"status\":\"ok\",\"topics\":[\"risk\"],\"days\":15}"));
        assertThrows(IllegalStateException.class, () -> controller.analyze(7, "auto", new ManagementAgentController.Request("售后", false)));
        verifyNoInteractions(access, sales, finance, risk);
        verify(python, never()).managementAnalysis(any());
    }

    @Test void salesAndFinanceFactsCarryTrimmedSkuAndLossSummaries() {
        when(permissions.allows(7L, "ai:sales:query")).thenReturn(true);
        when(permissions.allows(7L, "ai:finance:query")).thenReturn(true);
        var scope = new Scope("SHOP", 10L, 7L, 10L);
        when(access.scope(7L)).thenReturn(scope);
        var now = LocalDateTime.now();
        var metrics = new AiSalesReadService.Metrics(5, 3, BigDecimal.TEN);
        when(sales.overview(7, scope)).thenReturn(new AiSalesReadService.Overview("绑定店铺", 7, now, now, now, metrics, metrics, List.of(), List.of(), List.of(), List.of(), new AiSalesReadService.Quality(0, 0), "未扣退款"));
        when(sales.skuPerformance(7, scope, 100)).thenReturn(skuPerf(3));
        var orders = new AiFinanceReadService.Orders(2, BigDecimal.TEN, 0);
        var refunds = new AiFinanceReadService.Refunds(1, BigDecimal.ONE, 0, 0, 0);
        var settlement = new AiFinanceReadService.Settlement(true, 1, BigDecimal.TEN, null, null, null, null, null, null, BigDecimal.TEN, 0, 0);
        var ledger = new AiFinanceReadService.Ledger(true, 0, null, null, 0, List.of(), "");
        when(finance.overview(7, scope)).thenReturn(new AiFinanceReadService.Overview("绑定店铺", 7, now, now, now, orders, refunds, BigDecimal.TEN, BigDecimal.TEN, settlement, ledger, List.of(), "财务限制"));
        when(finance.lossOrders(7, scope)).thenReturn(new AiFinanceReadService.LossOrders(true, 7, now, now, now, 2, false, List.of(),
                java.util.Map.of("运费占比过高", 2L), "亏损限制"));
        controller.analyze(7, "both", null);
        verify(python).managementAnalysis(argThat(value -> {
            var facts = (ManagementAgentController.Facts) value;
            return facts.sales().sku() != null && facts.sales().sku().skuCount() == 3 && facts.sales().sku().top5Share() == 500
                    && facts.sales().sku().quadrants().get("lowSalesLowRefund") == 2 && facts.sales().sku().limitation().equals("SKU限制")
                    && facts.finance().loss() != null && facts.finance().loss().count() == 2
                    && facts.finance().loss().causes().get("运费占比过高") == 2L && facts.finance().loss().limitation().equals("亏损限制");
        }));
    }

    @Test void unavailableLossDataIsNotSentAsZeroLosses() {
        when(permissions.allows(7L, "ai:finance:query")).thenReturn(true);
        var scope = new Scope("SHOP", 10L, 7L, 10L);
        when(access.scope(7L)).thenReturn(scope);
        var now = LocalDateTime.now();
        var orders = new AiFinanceReadService.Orders(2, BigDecimal.TEN, 0);
        var refunds = new AiFinanceReadService.Refunds(1, BigDecimal.ONE, 0, 0, 0);
        var settlement = new AiFinanceReadService.Settlement(false, 0, null, null, null, null, null, null, null, null, 0, 0);
        var ledger = new AiFinanceReadService.Ledger(true, 0, null, null, 0, List.of(), "");
        when(finance.overview(7, scope)).thenReturn(new AiFinanceReadService.Overview("绑定店铺", 7, now, now, now, orders, refunds, BigDecimal.TEN, null, settlement, ledger, List.of(), "财务限制"));
        when(finance.lossOrders(7, scope)).thenReturn(new AiFinanceReadService.LossOrders(false, 7, now, now, now, 0, false, List.of(), java.util.Map.of(), "结算单表尚未建立"));
        controller.analyze(7, "finance", null);
        verify(python).managementAnalysis(argThat(value -> {
            var facts = (ManagementAgentController.Facts) value;
            return facts.finance() != null && facts.finance().loss() == null && facts.sales() == null;
        }));
    }
}
