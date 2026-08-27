package cn.xinghuaerp.open.wei.vo.ewaybill;

import cn.xinghuaerp.open.wei.vo.BaseResVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class DeliveryGetVo extends BaseResVo {

    private List<DeliveryVo> list;

    private String shop_id;

}
