package cn.xinghuaerp.order.facade;

import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.vo.SalesDailyVo;
import java.util.List;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface OOrderFacade {
    OOrder queryDetailByOrderNum(String orderNum);
    List<SalesDailyVo> salesDaily();
    SalesDailyVo getTodaySalesDaily(Long merchantId);
    Integer getWaitShipOrderAllCount(Long merchantId);
    OOrder getById(java.io.Serializable id);
    java.util.List<OOrder> list();
    java.util.List<OOrder> list(com.baomidou.mybatisplus.core.conditions.Wrapper<OOrder> query);
    boolean updateById(OOrder entity);
}
