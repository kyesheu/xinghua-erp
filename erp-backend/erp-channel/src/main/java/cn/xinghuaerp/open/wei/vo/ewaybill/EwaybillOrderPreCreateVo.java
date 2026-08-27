package cn.xinghuaerp.open.wei.vo.ewaybill;

import cn.xinghuaerp.open.wei.vo.BaseResVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class EwaybillOrderPreCreateVo extends BaseResVo {
    private String ewaybill_order_id;

}
