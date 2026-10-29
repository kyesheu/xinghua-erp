package cn.xinghuaerp.inventory.controller;
import cn.xinghuaerp.inventory.service.InventoryReconciliationService;
import cn.xinghuaerp.system.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class InventoryReconciliationAccessTest {
 @Test void missingPermissionRejectsBeforeResolvingOrReadingBusinessData() {
  var service=mock(InventoryReconciliationService.class);var access=mock(BusinessAccessService.class);var permissions=mock(AiAgentPermissionService.class);
  var controller=new InventoryReconciliationController(service,access,permissions){public Long getUserId(){return 7L;}};
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.inspect(0,100));
  verifyNoInteractions(service,access);
  when(permissions.allows(7L,"wms:inventory:query")).thenReturn(true);
  var scope=new BusinessSubjectScopeService.Scope("SHOP",10L,7L,10L);when(access.scope(7L)).thenReturn(scope);
  controller.inspect(20,100);verify(service).inspect(scope,20,100);
 }
}
