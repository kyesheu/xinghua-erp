package cn.xinghuaerp.ai.controller;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.ai.service.AiRiskReadService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.controller.AiRiskReadController;
class AiRiskReadControllerTest {
 JdbcTemplate db; AiAgentPermissionService permissions; BusinessAccessService access; AiRiskReadService risk; AiRiskReadController controller;
 @BeforeEach void setup(){
  db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:risk"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  db.execute("CREATE TABLE o_refund(id BIGINT,refund_num VARCHAR(50),order_num VARCHAR(50),merchant_id BIGINT,shop_id BIGINT,refund_type INT,goods_name VARCHAR(100),goods_sku VARCHAR(100),quantity BIGINT,refund_fee DECIMAL,status INT,erp_status INT,create_time TIMESTAMP,update_time TIMESTAMP)");
  db.update("INSERT INTO o_refund(id,merchant_id,shop_id) VALUES(1,10,100),(2,10,101),(3,20,200)");
  permissions=mock(AiAgentPermissionService.class);access=mock(BusinessAccessService.class);risk=mock(AiRiskReadService.class);when(permissions.allows(1L,"ai:risk:query")).thenReturn(true);
  controller=new AiRiskReadController(permissions,access,db,risk){@Override public Long getUserId(){return 1L;}};
 }
 long count(){return ((Number)((Map<?,?>)controller.refunds(1).get("data")).get("total")).longValue();}
 @Test void isolates(){when(access.scope(1L)).thenReturn(new Scope("HEADQUARTERS",null,null,null));assertEquals(3,count());when(access.scope(1L)).thenReturn(new Scope("MERCHANT",10L,10L,null));assertEquals(2,count());when(access.scope(1L)).thenReturn(new Scope("SHOP",100L,10L,100L));assertEquals(1,count());}
 @Test void denies(){when(permissions.allows(1L,"ai:risk:query")).thenReturn(false);assertThrows(AccessDeniedException.class,()->controller.refunds(1));when(permissions.allows(1L,"ai:risk:query")).thenReturn(true);when(access.scope(1L)).thenReturn(new Scope("WAREHOUSE",1L,null,null));assertThrows(AccessDeniedException.class,()->controller.refunds(1));}
 @Test void overviewDeniesUnauthorizedAndUnsupportedSubjects(){when(permissions.allows(1L,"ai:risk:query")).thenReturn(false);assertThrows(AccessDeniedException.class,()->controller.overview());verifyNoInteractions(risk);when(permissions.allows(1L,"ai:risk:query")).thenReturn(true);when(access.scope(1L)).thenReturn(new Scope("WAREHOUSE",1L,null,null));assertThrows(AccessDeniedException.class,()->controller.overview());verifyNoInteractions(risk);}
 @Test void overviewReturnsServiceDataWithoutTouchingRefunds(){when(access.scope(1L)).thenReturn(new Scope("SHOP",100L,10L,100L));var data=new AiRiskReadService.Overview("绑定店铺",5,2,List.of(new AiRiskReadService.Finding("STALE_PENDING","HIGH","待处理超3天",1,"核对原单与售后进度")),"限制");when(risk.overview(new Scope("SHOP",100L,10L,100L))).thenReturn(data);assertSame(data,controller.overview().get("data"));verify(risk).overview(new Scope("SHOP",100L,10L,100L));when(access.scope(1L)).thenReturn(new Scope("HEADQUARTERS",null,null,null));assertEquals(3,count());}
 /** 前端概览卡直接渲染 scope 与 limitation，用最严格的 NON_NULL mapper 序列化后仍必须存在。 */
 @Test void overviewSerializesScopeAndLimitationForFrontend(){when(access.scope(1L)).thenReturn(new Scope("HEADQUARTERS",null,null,null));var data=new AiRiskReadService.Overview("总部",5,2,List.of(),AiRiskReadService.LIMITATION);when(risk.overview(new Scope("HEADQUARTERS",null,null,null))).thenReturn(data);var json=JsonUtils.readTree(JsonUtils.write(controller.overview().get("data")));assertTrue(json.get("scope").isString());assertEquals("总部",json.get("scope").asString());assertTrue(json.get("limitation").isString());assertFalse(json.get("limitation").asString().isEmpty());assertEquals(5,json.get("total").asInt());assertEquals(2,json.get("open").asInt());assertTrue(json.get("findings").isArray());}
}
