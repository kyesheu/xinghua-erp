package cn.xinghuaerp.finance.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.finance.entity.FmsOrderSettlement;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.finance.query.OrderSettlementQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

public interface FmsOrderSettlementService {

    PageResult<FmsOrderSettlement> queryPageList(OrderSettlementQuery query, PageQuery pageQuery);

    List<OOrder> getUnsettledOrders(OrderSettlementQuery query, Page<OOrder> page);

    List<FmsOrderSettlement> getSettledOrders(OrderSettlementQuery query, Page<FmsOrderSettlement> page);

    Map<String, Object> autoSettlement(List<String> orderIds, String operator);

    Map<String, Object> manualSettlement(String orderId, String settlementId, String operator);

    boolean cancelSettlement(Long id, String operator);

    FmsOrderSettlement getSettlementDetail(Long id);

    FmsOrderSettlement calculateOrderSettlement(String orderId);
    java.util.Map<String,Object> autoSettlement(java.util.List<String> orderIds,String operator,cn.xinghuaerp.system.service.SalesScope scope);
}
