package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.ai.service.AiCsvExportService;
import cn.xinghuaerp.ai.service.AiFinanceReadService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.controller.AiFinanceReadController;

class AiFinanceReadControllerTest {
    final AiFinanceReadService finance=mock(AiFinanceReadService.class);
    final BusinessAccessService access=mock(BusinessAccessService.class);
    final AiAgentPermissionService permissions=mock(AiAgentPermissionService.class);
    final AiFinanceReadController controller=new AiFinanceReadController(finance,access,permissions,new AiCsvExportService());
    final Scope scope=new Scope("SHOP",10L,7L,10L);

    @BeforeEach void login() {
        var user=new LoginUser();user.setUserId(7L);user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void financeNeedsItsOwnCurrentPermissionAndServerScope() {
        when(permissions.allows(7L,"ai:sales:query")).thenReturn(true);
        assertThrows(AccessDeniedException.class,()->controller.overview(7));verifyNoInteractions(finance,access);
        when(permissions.allows(7L,"ai:finance:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(scope);
        controller.overview(7);verify(finance).overview(7,scope);
    }

    private AiFinanceReadService.LossOrders sampleLoss(boolean available) {
        var now=LocalDateTime.now();
        var order=new AiFinanceReadService.LossOrder("O1",new BigDecimal("100"),new BigDecimal("0"),new BigDecimal("40"),
                new BigDecimal("30"),new BigDecimal("20"),new BigDecimal("15"),new BigDecimal("105"),new BigDecimal("-5"),
                List.of("费用占比过高","运费占比过高","零成本待核实"));
        return new AiFinanceReadService.LossOrders(available,7,now,now,now,available?1:0,false,
                available?List.of(order):List.of(),available?Map.of("费用占比过高",1L):Map.of(),
                available?"亏损限制":"结算单表尚未建立");
    }

    @Test void lossOrdersRequiresPermissionAndUsesServerScope() {
        when(access.scope(7L)).thenReturn(scope);
        assertThrows(AccessDeniedException.class,()->controller.lossOrders(7));verifyNoInteractions(finance);
        when(permissions.allows(7L,"ai:finance:query")).thenReturn(true);
        controller.lossOrders(7);
        verify(finance).lossOrders(7,scope);
        // days 校验由服务层执行。
        when(finance.lossOrders(8,scope)).thenThrow(new IllegalArgumentException("仅支持7或30个完整自然日"));
        assertThrows(IllegalArgumentException.class,()->controller.lossOrders(8));
    }

    @Test void lossOrdersExportReturnsCsvAttachmentWithBomAndNote() {
        when(permissions.allows(7L,"ai:finance:query")).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->controller.exportLossOrders(7));
        when(permissions.allows(7L,"ai:finance:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(scope);
        when(finance.lossOrders(7,scope)).thenReturn(sampleLoss(true));
        ResponseEntity<byte[]> response=controller.exportLossOrders(7);
        assertEquals(200,response.getStatusCode().value());
        assertEquals(MediaType.parseMediaType("text/csv;charset=UTF-8"),response.getHeaders().getContentType());
        String disposition=response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);assertTrue(disposition.startsWith("attachment; filename=loss-orders-7d-"));
        String text=new String(response.getBody(),StandardCharsets.UTF_8);
        assertEquals('\uFEFF',text.charAt(0));
        assertTrue(text.contains("\"订单号\""));
        assertTrue(text.contains("\"O1\""));
        assertTrue(text.contains("\"费用占比过高；运费占比过高；零成本待核实\""));
        assertTrue(text.contains("\"说明\",\"亏损限制\""));
    }

    @Test void shopComparisonRequiresPermissionAndUsesServerScope() {
        when(access.scope(7L)).thenReturn(scope);
        assertThrows(AccessDeniedException.class,()->controller.shopComparison(7));verifyNoInteractions(finance);
        when(permissions.allows(7L,"ai:finance:query")).thenReturn(true);
        controller.shopComparison(7);
        verify(finance).shopComparison(7,scope);
    }
}
