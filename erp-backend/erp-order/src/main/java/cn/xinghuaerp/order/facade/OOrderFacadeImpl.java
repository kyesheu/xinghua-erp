package cn.xinghuaerp.order.facade;

import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.vo.SalesDailyVo;
import java.util.List;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class OOrderFacadeImpl implements OOrderFacade {
    private final cn.xinghuaerp.order.service.OOrderService delegate;
    @Override public OOrder queryDetailByOrderNum(String orderNum) { return delegate.queryDetailByOrderNum(orderNum); }
    @Override public List<SalesDailyVo> salesDaily() { return delegate.salesDaily(); }
    @Override public SalesDailyVo getTodaySalesDaily(Long merchantId) { return delegate.getTodaySalesDaily(merchantId); }
    @Override public Integer getWaitShipOrderAllCount(Long merchantId) { return delegate.getWaitShipOrderAllCount(merchantId); }
    @Override public OOrder getById(java.io.Serializable id) { return delegate.getById(id); }
    @Override public java.util.List<OOrder> list() { return delegate.list(); }
    @Override public java.util.List<OOrder> list(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrder> query) { return delegate.list(query); }
    @Override public boolean updateById(OOrder entity) { return delegate.updateById(entity); }
}
