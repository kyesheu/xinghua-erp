package cn.xinghuaerp.platform.controller;

import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.ResultVoEnum;
import cn.xinghuaerp.common.api.ShopApiParams;
import cn.xinghuaerp.enums.EnumShopType;
import cn.xinghuaerp.enums.HttpStatus;
import cn.xinghuaerp.system.entity.OShopPlatform;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.dou.DouTokenApiHelper;
import cn.xinghuaerp.open.dou.model.Token;
import cn.xinghuaerp.system.facade.OShopPlatformFacade;
import cn.xinghuaerp.system.facade.OShopFacade;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@AllArgsConstructor
@Component
public class DouApiCommon {
    private final OShopFacade shopService;
    private final OShopPlatformFacade platformService;

    public ResultVo<ShopApiParams> checkBefore(Long shopId) {
        var shop = shopService.getById(shopId);
        if (shop == null) {
            return ResultVo.error(HttpStatus.PARAMS_ERROR, "参数错误，没有找到店铺");
        }
        if (shop.getType() != EnumShopType.DOU.getIndex()) {
            return ResultVo.error(HttpStatus.PARAMS_ERROR, "参数错误，店铺不是抖店店铺");
        }
        if (!StringUtils.hasLength(shop.getSellerId())) {
            return ResultVo.error(HttpStatus.PARAMS_ERROR, "参数错误，请设置抖店平台店铺ID（shopId）");
        }
        String appKey = shop.getAppKey();
        String appSecret = shop.getAppSecret();
        if(!StringUtils.hasLength(appKey) || !StringUtils.hasLength(appSecret)) {
            OShopPlatform platform = platformService.getById(EnumShopType.DOU.getIndex());
            appKey = platform.getAppKey();
            appSecret = platform.getAppSecret();
        }

        if (!StringUtils.hasText(appKey)) {
            return ResultVo.error(HttpStatus.PARAMS_ERROR, "店铺配置错误，没有找到AppKey");
        }
        if (!StringUtils.hasText(appSecret)) {
            return ResultVo.error(HttpStatus.PARAMS_ERROR, "店铺配置错误，没有找到AppSecret");
        }

        ShopApiParams params = new ShopApiParams();
        params.setAppKey(appKey);
        params.setAppSecret(appSecret);
        params.setRedirectUri(shop.getApiCallbackUrl());
        params.setServerUrl(shop.getApiRequestUrl());
        params.setSellerId(shop.getSellerId());
        String accessToken = shop.getAccessToken();

        if (!StringUtils.hasText(shop.getAccessToken())) {
            ApiResultVo<Token> token = DouTokenApiHelper.getToken(appKey, appSecret, shop.getId());
            if(token.getCode()!=0) {
                return ResultVo.error(ResultVoEnum.API_FAIL.getIndex(), token.getMsg(), params);
            }else{
                shopService.updateSessionKey(shopId,token.getData().getAccessToken(),token.getData().getRefreshToken());
                params.setAccessToken(token.getData().getAccessToken());
                accessToken  = token.getData().getAccessToken();
            }
        }else{
            if(StringUtils.hasText(shop.getRefreshToken())) {
                ApiResultVo<Token> token1 = DouTokenApiHelper.getToken(appKey, appSecret, shop.getId());
                if (token1.getCode() == 0) {
                    shopService.updateSessionKey(shopId, token1.getData().getAccessToken(), token1.getData().getRefreshToken());
                    params.setAccessToken(token1.getData().getAccessToken());
                    accessToken = token1.getData().getAccessToken();
                }else{
                    return ResultVo.error(ResultVoEnum.API_FAIL.getIndex(), token1.getMsg(), params);
                }
            }
        }
        params.setAccessToken(accessToken);
        return ResultVo.success(HttpStatus.SUCCESS, params);
    }
}
