package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.system.bo.ShopTokenBo;
import java.util.List;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class OShopFacadeImpl implements OShopFacade {
    private final cn.xinghuaerp.system.service.OShopService delegate;
    @Override public OShop selectShopById(Long id) { return delegate.selectShopById(id); }
    @Override public OShop selectShopBySellerId(String sellerId) { return delegate.selectShopBySellerId(sellerId); }
    @Override public void updateSessionKey(Long shopId,String sessionKey) { delegate.updateSessionKey(shopId, sessionKey); }
    @Override public void updateSessionKey(Long shopId, String token,String refreshToken) { delegate.updateSessionKey(shopId, token, refreshToken); }
    @Override public void updateSessionKey(Long shopId, String token,String refreshToken,Long expiresIn) { delegate.updateSessionKey(shopId, token, refreshToken, expiresIn); }
    @Override public void updateSessionKey(ShopTokenBo tokenBo) { delegate.updateSessionKey(tokenBo); }
    @Override public OShop getById(java.io.Serializable id) { return delegate.getById(id); }
    @Override public java.util.List<OShop> list() { return delegate.list(); }
    @Override public java.util.List<OShop> list(com.baomidou.mybatisplus.core.conditions.Wrapper<OShop> query) { return delegate.list(query); }
    @Override public long count() { return delegate.count(); }
}
