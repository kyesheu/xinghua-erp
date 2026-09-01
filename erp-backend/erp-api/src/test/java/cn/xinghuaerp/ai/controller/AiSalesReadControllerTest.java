package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.ai.service.AiCsvExportService;
import cn.xinghuaerp.ai.service.AiSalesReadService;
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
import cn.xinghuaerp.ai.controller.AiSalesReadController;

class AiSalesReadControllerTest {
    final AiSalesReadService sales=mock(AiSalesReadService.class);
    final BusinessAccessService access=mock(BusinessAccessService.class);
    final AiAgentPermissionService permissions=mock(AiAgentPermissionService.class);
    final AiSalesReadController controller=new AiSalesReadController(sales,access,permissions,new AiCsvExportService());
    final Scope scope=new Scope("SHOP",10L,7L,10L);

    @BeforeEach void login() {
        var user=new LoginUser();user.setUserId(7L);user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void currentPermissionAndServerScopeAreRequired() {
        var user=new LoginUser();user.setUserId(7L);user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
        assertThrows(AccessDeniedException.class,()->controller.overview(7));verifyNoInteractions(sales,access);
        when(permissions.allows(7L,"ai:sales:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(scope);
        controller.overview(7);verify(sales).overview(7,scope);
    }

    private AiSalesReadService.SkuPerformance sample() {
        var now=LocalDateTime.now();
        var row=new AiSalesReadService.SkuRow("5","A",2,3,new BigDecimal("90"),2,new BigDecimal("35"),
                new BigDecimal("0.388889"),"highSalesHighRefund");
        return new AiSalesReadService.SkuPerformance(7,now,now,now,1,0,null,null,
                Map.of("top5",1000,"top10",1000,"top20",1000),Map.of("highSalesHighRefund",1),
                Map.of("highSalesHighRefund",new BigDecimal("90")),List.of(row),"SKU限制");
    }

    @Test void skuPerformanceRequiresPermissionAndUsesServerScope() {
        when(access.scope(7L)).thenReturn(scope);
        assertThrows(AccessDeniedException.class,()->controller.skuPerformance(7));verifyNoInteractions(sales);
        when(permissions.allows(7L,"ai:sales:query")).thenReturn(true);
        controller.skuPerformance(7);
        verify(sales).skuPerformance(7,scope,100);
        // days 校验由服务层执行：服务抛出的参数错误原样传播。
        when(sales.skuPerformance(8,scope,100)).thenThrow(new IllegalArgumentException("仅支持7或30个完整自然日"));
        assertThrows(IllegalArgumentException.class,()->controller.skuPerformance(8));
    }

    @Test void skuPerformanceExportReturnsCsvAttachmentWithBomAndNote() {
        when(permissions.allows(7L,"ai:sales:query")).thenReturn(true);
        when(access.scope(7L)).thenReturn(scope);
        when(sales.skuPerformance(7,scope,5000)).thenReturn(sample());
        denyPermission();
        assertThrows(AccessDeniedException.class,()->controller.exportSkuPerformance(7));
        when(permissions.allows(7L,"ai:sales:query")).thenReturn(true);
        ResponseEntity<byte[]> response=controller.exportSkuPerformance(7);
        assertEquals(200,response.getStatusCode().value());
        assertEquals(MediaType.parseMediaType("text/csv;charset=UTF-8"),response.getHeaders().getContentType());
        String disposition=response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);assertTrue(disposition.startsWith("attachment; filename=sku-performance-7d-"));
        String text=new String(response.getBody(),StandardCharsets.UTF_8);
        assertEquals('\uFEFF',text.charAt(0));
        assertTrue(text.contains("\"内部SKU ID\""));
        assertTrue(text.contains("\"5\",\"A\""));
        assertTrue(text.contains("\"高销量高退款\""));
        assertTrue(text.contains("\"说明\",\"SKU限制\""));
    }
    private void denyPermission() {
        when(permissions.allows(7L,"ai:sales:query")).thenReturn(false);
    }
}
