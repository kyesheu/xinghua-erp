package cn.xinghuaerp.finance.service;

import cn.xinghuaerp.finance.service.impl.FmsOrderSettlementServiceImpl;
import cn.xinghuaerp.finance.query.OrderSettlementQuery;
import cn.xinghuaerp.order.facade.OrderFacade;
import cn.xinghuaerp.order.entity.OOrder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementScopeTest {
    @Test
    void bothUnsettledBranchesRespectMerchantAndShop() throws Exception {
        var orders = mock(OrderFacade.class);
        var service = new FmsOrderSettlementServiceImpl();
        var field = FmsOrderSettlementServiceImpl.class.getDeclaredField("orderMapper");
        field.setAccessible(true); field.set(service, orders);
        var query = new OrderSettlementQuery(); query.setMerchantId(10L); query.setShopId(20L);
        service.getUnsettledOrders(query, new Page<OOrder>());
        ArgumentCaptor<QueryWrapper<OOrder>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orders).pageOOrder(any(),captor.capture());
        var wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        for (var entry : wrapper.getParamNameValuePairs().entrySet())
            sql = sql.replace("#{ew.paramNameValuePairs."+entry.getKey()+"}",entry.getValue().toString());
        try (var conn = java.sql.DriverManager.getConnection("jdbc:h2:mem:settlement_scope"); var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE orders(id int,settlement_status int,merchant_id bigint,shop_id bigint,create_time timestamp)");
            stmt.execute("INSERT INTO orders VALUES(1,0,10,20,NULL),(2,NULL,10,20,NULL),(3,0,99,20,NULL),(4,NULL,10,99,NULL),(5,1,10,20,NULL)");
            var rs = stmt.executeQuery("SELECT id FROM orders WHERE "+sql);
            var ids = new java.util.HashSet<Integer>(); while(rs.next()) ids.add(rs.getInt(1));
            assertEquals(java.util.Set.of(1,2),ids);
        }
    }
}
