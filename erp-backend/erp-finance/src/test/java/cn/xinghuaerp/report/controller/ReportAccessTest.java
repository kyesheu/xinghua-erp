package cn.xinghuaerp.report.controller;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.order.facade.OOrderFacade;
import cn.xinghuaerp.system.facade.OShopFacade;
import cn.xinghuaerp.report.controller.ReportController;
class ReportAccessTest {
 @AfterEach void clear() { SecurityContextHolder.clearContext(); }
 @Test void nonHeadquartersCannotReadUnscopedReports() {
  var user=new LoginUser(); user.setUserId(7L);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
  var access=mock(BusinessAccessService.class); doThrow(new AccessDeniedException("denied")).when(access).requireHeadquarters(7L);
  var orders=mock(OOrderFacade.class); var shops=mock(OShopFacade.class);
  var controller=new ReportController(orders,shops,access);
  assertThrows(AccessDeniedException.class,controller::todayDaily); assertThrows(AccessDeniedException.class,controller::salesDaily);
  verifyNoInteractions(orders,shops);
 }
}
