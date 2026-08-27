package cn.xinghuaerp.open.wei.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class RefundListVo extends BaseResVo {

    private Long[] after_sale_order_id_list;

    private boolean has_more;

}
