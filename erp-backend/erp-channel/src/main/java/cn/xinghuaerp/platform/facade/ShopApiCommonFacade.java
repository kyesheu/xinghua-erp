package cn.xinghuaerp.platform.facade;

import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.api.ShopApiParams;

/** 模块对外契约，仅暴露当前跨域流程所需操作。 */
public interface ShopApiCommonFacade {
    cn.xinghuaerp.common.ResultVo<cn.xinghuaerp.common.api.ShopApiParams> checkBefore(Long shopId, Integer shopType);
}
