package cn.xinghuaerp.finance.controller;
import cn.xinghuaerp.finance.service.SettlementReconciliationService;
import cn.xinghuaerp.system.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SettlementReconciliationAccessTest {
 @Test void permissionIsRequiredBeforeAnyDataLookup() {
  var service=mock(SettlementReconciliationService.class);var access=mock(BusinessAccessService.class);var permissions=mock(AiAgentPermissionService.class);
  var controller=new SettlementReconciliationController(service,access,permissions){public Long getUserId(){return 7L;}};
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.inspect(0,100));verifyNoInteractions(service,access);
  when(permissions.allows(7L,"ai:finance:query")).thenReturn(true);var scope=new BusinessSubjectScopeService.Scope("SHOP",10L,7L,10L);when(access.scope(7L)).thenReturn(scope);
  controller.inspect(5,100);verify(service).inspect(scope,5,100);
 }
}
