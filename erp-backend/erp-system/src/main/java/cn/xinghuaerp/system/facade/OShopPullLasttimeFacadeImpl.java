package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.OShopPullLasttime;
import java.util.List;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class OShopPullLasttimeFacadeImpl implements OShopPullLasttimeFacade {
    private final cn.xinghuaerp.system.service.OShopPullLasttimeService delegate;
    @Override public OShopPullLasttime getLasttimeByShop(Long shopId, String pullType) { return delegate.getLasttimeByShop(shopId, pullType); }
    @Override public List<OShopPullLasttime> getLasttimeByShop(Long shopId) { return delegate.getLasttimeByShop(shopId); }
    @Override public boolean save(OShopPullLasttime entity) { return delegate.save(entity); }
    @Override public boolean updateById(OShopPullLasttime entity) { return delegate.updateById(entity); }
    @Override public boolean advance(Long shopId,String type,java.time.LocalDateTime time){return delegate.advance(shopId,type,time);}
}
