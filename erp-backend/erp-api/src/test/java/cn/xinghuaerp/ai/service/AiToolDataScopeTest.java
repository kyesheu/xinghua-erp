package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.ai.service.impl.*;
import cn.xinghuaerp.order.entity.ShopOrder;
import cn.xinghuaerp.order.service.ShopOrderService;
import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import cn.xinghuaerp.system.service.SalesScope;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiToolDataScopeTest {
    private void metadata(Class<?> type) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "offline-test"), type);
    }
    @Test void shopOrderQueryCarriesBothOwnershipConditions() {
        metadata(ShopOrder.class);
        var service = mock(ShopOrderService.class);
        when(service.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<ShopOrder> query = call.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("merchant_id ="));
            assertTrue(sql.contains("shop_id ="));
            assertTrue(query.getParamNameValuePairs().containsValue(7L));
            assertTrue(query.getParamNameValuePairs().containsValue(10L));
            return java.util.List.of();
        });
        new OrderTools(service).getOrderList(7,null,100,new SalesScope(7L,10L));
        verify(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
    @Test void warehouseInventoryCannotReadOtherWarehousesAndInvalidScopeDoesNotQuery() {
        metadata(OGoodsInventory.class);
        var service = mock(OGoodsInventoryService.class);
        when(service.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<OGoodsInventory> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("warehouse_id ="));
            assertTrue(query.getSqlSegment().contains("is_delete ="));
            assertTrue(query.getParamNameValuePairs().containsValue(12L));
            return java.util.List.of();
        });
        var tool = new InventoryTools(service);
        tool.getInventoryList(5,100,new Scope("WAREHOUSE",12L,null,null));
        assertThrows(RuntimeException.class, () -> tool.getInventoryList(5,100,new Scope("SUPPLIER",1L,null,null)));
        assertThrows(RuntimeException.class, () -> tool.getInventoryList(5,100,new Scope("SHOP",1L,null,1L)));
        verify(service, times(1)).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
}
