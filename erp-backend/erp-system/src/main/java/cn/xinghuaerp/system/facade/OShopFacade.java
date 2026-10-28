package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.OShop;
import cn.xinghuaerp.system.bo.ShopTokenBo;
import java.util.List;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface OShopFacade {
    OShop selectShopById(Long id);
    OShop selectShopBySellerId(String sellerId);
    void updateSessionKey(Long shopId,String sessionKey);
    void updateSessionKey(Long shopId, String token,String refreshToken);
    void updateSessionKey(Long shopId, String token,String refreshToken,Long expiresIn);
    void updateSessionKey(ShopTokenBo tokenBo);
    OShop getById(java.io.Serializable id);
    java.util.List<OShop> list();
    java.util.List<OShop> list(com.baomidou.mybatisplus.core.conditions.Wrapper<OShop> query);
}
