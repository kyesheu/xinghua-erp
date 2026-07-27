package cn.xinghuaerp.product.vo;

import cn.xinghuaerp.product.entity.PddGoodsSku;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class PddGoodsSkuListVo extends PddGoodsSku {

    private String outerErpSkuId;

}
