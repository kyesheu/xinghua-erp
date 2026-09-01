package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.ai.service.PythonAgentClient;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.xinghuaerp.ai.service.AiInventoryReadService;
import cn.xinghuaerp.ai.controller.AiProductReadController;
class AiProductReadControllerTest {
    JdbcTemplate db; AiAgentPermissionService permissions; BusinessAccessService access; AiInventoryReadService inventory; PythonAgentClient python; AiProductReadController controller;
    @BeforeEach void setup() {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:product"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        for(String sql: List.of(
            "CREATE TABLE o_goods_sku(id BIGINT,merchant_id BIGINT,shop_id BIGINT,goods_name VARCHAR(100),sku_code VARCHAR(40),sku_name VARCHAR(40))",
            "CREATE TABLE o_goods_inventory(sku_id BIGINT,merchant_id BIGINT,shop_id BIGINT,is_delete INT)",
            "CREATE TABLE o_shop(id BIGINT,name VARCHAR(100))",
            "CREATE TABLE oms_shop_goods_sku(id BIGINT,merchant_id BIGINT,shop_id BIGINT,erp_goods_sku_id BIGINT,product_id VARCHAR(30),product_title VARCHAR(100),sku_id VARCHAR(30),sku_name VARCHAR(30),status INT,update_on TIMESTAMP)",
            "CREATE TABLE erp_supplier(id BIGINT,name VARCHAR(100))",
            "CREATE TABLE erp_supplier_product(id BIGINT,supplier_id BIGINT,merchant_id BIGINT,product_name VARCHAR(100))",
            "CREATE TABLE erp_supplier_product_item(id BIGINT,supplier_product_id BIGINT,supplier_id BIGINT,erp_goods_sku_id BIGINT,sku_code VARCHAR(30),standard VARCHAR(30),status INT)")) db.execute(sql);
        db.update("INSERT INTO o_goods_sku VALUES(1,10,100,'test','sku','blue'),(2,20,200,'other','other','size')");
        db.update("INSERT INTO o_shop VALUES(100,'one'),(101,'two'),(200,'other')");
        db.update("INSERT INTO oms_shop_goods_sku VALUES(1,10,100,1,'p1','one','a','s',1,NULL),(2,10,101,1,'p2','two','b','s',1,NULL),(3,20,200,1,'p3','other','c','s',1,NULL)");
        db.update("INSERT INTO erp_supplier VALUES(1,'supplier one'),(2,'supplier two')");
        db.update("INSERT INTO erp_supplier_product VALUES(1,1,10,'one'),(2,2,20,'two')");
        db.update("INSERT INTO erp_supplier_product_item VALUES(1,1,1,1,'a','s',1),(2,2,2,1,'b','s',1)");
        permissions=mock(AiAgentPermissionService.class); access=mock(BusinessAccessService.class); inventory=mock(AiInventoryReadService.class);
        when(permissions.allows(1L,"ai:product:query")).thenReturn(true);
        when(inventory.find(anyLong(),isNull(),any())).thenReturn(new AiInventoryReadService.Snapshot(List.of(),"test","test"));
        python=mock(PythonAgentClient.class); controller=new AiProductReadController(permissions,access,inventory,db,python){@Override public Long getUserId(){return 1L;}};
    }
    @SuppressWarnings("unchecked") Map<String,Object> data(){return (Map<String,Object>)controller.dossier(1L).get("data");}
    @Test void scopesAssociationsAndRejectsOtherSku(){
        when(access.scope(1L)).thenReturn(new Scope("MERCHANT",10L,10L,null));
        var merchant=data(); assertEquals(2,((List<?>)merchant.get("shops")).size()); assertEquals(1,((List<?>)merchant.get("suppliers")).size());
        assertThrows(AccessDeniedException.class,()->controller.dossier(2L));
        when(access.scope(1L)).thenReturn(new Scope("SHOP",100L,10L,100L));
        var shop=data(); assertEquals(1,((List<?>)shop.get("shops")).size()); assertEquals(false,shop.get("suppliersVisible"));
        when(access.scope(1L)).thenReturn(new Scope("HEADQUARTERS",null,null,null));
        var headquarters=data(); assertEquals(3,((List<?>)headquarters.get("shops")).size()); assertEquals(2,((List<?>)headquarters.get("suppliers")).size());
    }
    @Test void draftRequiresScopedSkuAndUsesServerFacts() {
        when(access.scope(1L)).thenReturn(new Scope("SHOP",100L,10L,100L));
        assertThrows(AccessDeniedException.class,()->controller.draft(new AiProductReadController.DraftRequest(2L,"中文","")));
        verifyNoInteractions(python);
        controller.draft(new AiProductReadController.DraftRequest(1L,"中文","成人","美国站","旅行用品"));
        var captor=org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(python).productDraft(captor.capture());
        var facts=(Map<?,?>)captor.getValue();
        assertEquals("test",facts.get("goodsName")); assertEquals("sku",facts.get("skuCode")); assertEquals("blue",facts.get("skuName"));
        assertEquals("美国站",facts.get("site")); assertEquals("旅行用品",facts.get("confirmedKeywords"));
    }
    @Test void permissionAndUnsupportedSubjectsAreRejected(){
        when(permissions.allows(1L,"ai:product:query")).thenReturn(false); assertThrows(AccessDeniedException.class,()->controller.dossier(1L));
        when(permissions.allows(1L,"ai:product:query")).thenReturn(true); when(access.scope(1L)).thenReturn(new Scope("WAREHOUSE",1L,null,null));
        assertThrows(IllegalArgumentException.class,()->controller.dossier(1L));
    }
}
