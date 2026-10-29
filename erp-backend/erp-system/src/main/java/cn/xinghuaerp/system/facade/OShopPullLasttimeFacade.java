package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.OShopPullLasttime;
import java.util.List;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface OShopPullLasttimeFacade {
    OShopPullLasttime getLasttimeByShop(Long shopId, String pullType);
    List<OShopPullLasttime> getLasttimeByShop(Long shopId);
    boolean save(OShopPullLasttime entity);
    boolean updateById(OShopPullLasttime entity);
    boolean advance(Long shopId,String pullType,java.time.LocalDateTime time);
}
