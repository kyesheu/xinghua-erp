package cn.xinghuaerp.open.wei.vo.ewaybill;

import cn.xinghuaerp.open.wei.vo.BaseResVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class EwaybillOrderCreateVo extends BaseResVo {
    private String ewaybill_order_id;
    private String waybill_id;
    private String delivery_error_msg;
    private String print_info;
}
