package cn.xinghuaerp.ai.controller;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.ai.service.AiMarketingReadService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.controller.AiMarketingReadController;
class AiMarketingReadControllerTest {
 JdbcTemplate db; AiAgentPermissionService permissions; BusinessAccessService access; AiMarketingReadService marketing; AiMarketingReadController controller;
 @BeforeEach void setup(){
  db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:marketing"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  db.execute("CREATE TABLE o_marketing_discount_rule(id BIGINT,rule_name VARCHAR(50),discount_type INT,discount_value DECIMAL,apply_scope INT,apply_merchant_id BIGINT,apply_shop_id BIGINT,total_quota INT,used_quota INT,min_order_amount DECIMAL,start_time BIGINT,end_time BIGINT,status INT,priority INT,remark VARCHAR(50))");
  db.update("INSERT INTO o_marketing_discount_rule(id,apply_scope,apply_merchant_id,apply_shop_id) VALUES(1,1,0,0),(2,2,10,0),(3,3,10,100),(4,3,10,101),(5,2,20,0),(6,3,20,200)");
  permissions=mock(AiAgentPermissionService.class); access=mock(BusinessAccessService.class); marketing=mock(AiMarketingReadService.class);
  when(permissions.allows(1L,"ai:marketing:query")).thenReturn(true);
  controller=new AiMarketingReadController(permissions,access,db,marketing){@Override public Long getUserId(){return 1L;}};
 }
 long count(){return ((Number)((Map<?,?>)controller.rules(1).get("data")).get("total")).longValue();}
 @Test void filtersScopes(){when(access.scope(1L)).thenReturn(new Scope("HEADQUARTERS",null,null,null));assertEquals(6,count());when(access.scope(1L)).thenReturn(new Scope("MERCHANT",10L,10L,null));assertEquals(4,count());when(access.scope(1L)).thenReturn(new Scope("SHOP",100L,10L,100L));assertEquals(3,count());}
 @Test void deniesUnauthorized(){when(permissions.allows(1L,"ai:marketing:query")).thenReturn(false);assertThrows(AccessDeniedException.class,()->controller.rules(1));when(permissions.allows(1L,"ai:marketing:query")).thenReturn(true);when(access.scope(1L)).thenReturn(new Scope("WAREHOUSE",1L,null,null));assertThrows(AccessDeniedException.class,()->controller.rules(1));}
 @Test void overviewDeniesUnauthorizedAndUnsupportedSubjects(){when(permissions.allows(1L,"ai:marketing:query")).thenReturn(false);assertThrows(AccessDeniedException.class,()->controller.overview());verifyNoInteractions(marketing);when(permissions.allows(1L,"ai:marketing:query")).thenReturn(true);when(access.scope(1L)).thenReturn(new Scope("WAREHOUSE",1L,null,null));assertThrows(AccessDeniedException.class,()->controller.overview());verifyNoInteractions(marketing);}
 @Test void overviewReturnsServiceDataWithoutTouchingRules(){when(access.scope(1L)).thenReturn(new Scope("SHOP",100L,10L,100L));var data=new AiMarketingReadService.Overview("绑定店铺",3,2,1,List.of(new AiMarketingReadService.Finding("EXPIRED_ENABLED","HIGH","已过期但仍启用",1,"核对结束时间")),"限制");when(marketing.overview(new Scope("SHOP",100L,10L,100L))).thenReturn(data);assertSame(data,controller.overview().get("data"));verify(marketing).overview(new Scope("SHOP",100L,10L,100L));when(access.scope(1L)).thenReturn(new Scope("HEADQUARTERS",null,null,null));assertEquals(6,count());}
 /** 前端概览卡直接渲染 scope 与 limitation，用最严格的 NON_NULL mapper 序列化后仍必须存在。 */
 @Test void overviewSerializesScopeAndLimitationForFrontend(){when(access.scope(1L)).thenReturn(new Scope("SHOP",100L,10L,100L));var data=new AiMarketingReadService.Overview("绑定店铺",3,2,1,List.of(),AiMarketingReadService.LIMITATION);when(marketing.overview(new Scope("SHOP",100L,10L,100L))).thenReturn(data);var json=JsonUtils.readTree(JsonUtils.write(controller.overview().get("data")));assertTrue(json.get("scope").isString());assertEquals("绑定店铺",json.get("scope").asString());assertTrue(json.get("limitation").isString());assertFalse(json.get("limitation").asString().isEmpty());assertEquals(3,json.get("total").asInt());assertEquals(2,json.get("active").asInt());assertEquals(1,json.get("pendingReview").asInt());assertTrue(json.get("findings").isArray());}
}
