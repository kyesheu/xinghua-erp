package cn.xinghuaerp.system.service;

import cn.xinghuaerp.fulfillment.controller.ShipOrderCloudWarehouseController;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.system.security.SecurityConfig;
import cn.xinghuaerp.inventory.service.OOrderStockingService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.service.MethodPermissionService;

class MethodPermissionServiceTest {
 @Configuration @EnableMethodSecurity
 static class Config {
  @Bean AiAgentPermissionService permissions() { return mock(AiAgentPermissionService.class); }
  @Bean(name="ss") MethodPermissionService ss(AiAgentPermissionService p) { return new MethodPermissionService(p); }
  @Bean BusinessAccessService access() { return mock(BusinessAccessService.class); }
  @Bean OOrderStockingService stock() { return mock(OOrderStockingService.class); }
  @Bean ShipOrderCloudWarehouseController controller(OOrderStockingService s, BusinessAccessService a) { return new ShipOrderCloudWarehouseController(s,a); }
 }
 @Test void proxyUsesLivePermissionsAndDeniesBeforeBusinessCode() {
  assertNotNull(SecurityConfig.class.getAnnotation(EnableMethodSecurity.class));
  try (var context = new AnnotationConfigApplicationContext(Config.class)) {
   var user = new LoginUser(); user.setUserId(7L);
   SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
   var controller = context.getBean(ShipOrderCloudWarehouseController.class);
   var permissions = context.getBean(AiAgentPermissionService.class);
   var access = context.getBean(BusinessAccessService.class);
   assertThrows(AccessDeniedException.class, () -> controller.pushToCloudWarehouse(Map.of("ids",List.of(42L))));
   verifyNoInteractions(access);
   when(permissions.allows(7L,"shop:order:edit")).thenReturn(true);
   assertEquals(2001, controller.pushToCloudWarehouse(Map.of("ids",List.of(42L))).get("code"));
   verify(access).requireHeadquarters(7L);
   when(permissions.allows(7L,"shop:order:edit")).thenReturn(false);
   assertThrows(AccessDeniedException.class, () -> controller.pushToCloudWarehouse(Map.of("ids",List.of(42L))));
  } finally { SecurityContextHolder.clearContext(); }
 }
}
