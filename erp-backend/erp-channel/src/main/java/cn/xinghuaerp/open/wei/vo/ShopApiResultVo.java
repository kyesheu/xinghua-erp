package cn.xinghuaerp.open.wei.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ShopApiResultVo extends BaseResVo{
    private ShopInfoVo info;
}
