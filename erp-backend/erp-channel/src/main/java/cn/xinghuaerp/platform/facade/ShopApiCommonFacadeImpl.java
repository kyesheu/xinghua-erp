package cn.xinghuaerp.platform.facade;

import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.api.ShopApiParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class ShopApiCommonFacadeImpl implements ShopApiCommonFacade {
    private final cn.xinghuaerp.platform.service.ShopApiCommon delegate;
    @Override public cn.xinghuaerp.common.ResultVo<cn.xinghuaerp.common.api.ShopApiParams> checkBefore(Long shopId, Integer shopType) { return delegate.checkBefore(shopId, shopType); }
}
