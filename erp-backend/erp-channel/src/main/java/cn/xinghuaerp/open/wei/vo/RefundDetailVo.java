package cn.xinghuaerp.open.wei.vo;

import cn.xinghuaerp.open.wei.model.AfterSaleOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class RefundDetailVo extends BaseResVo {

    private AfterSaleOrder after_sale_order;

}
