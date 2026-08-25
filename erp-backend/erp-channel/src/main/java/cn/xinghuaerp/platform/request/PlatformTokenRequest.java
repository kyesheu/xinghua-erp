package cn.xinghuaerp.platform.request;

import lombok.Data;

/** 平台授权/令牌刷新入参。 */
@Data
public class PlatformTokenRequest {
    private Long shopId;
}
