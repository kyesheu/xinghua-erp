package cn.xinghuaerp.open.wei.vo.ewaybill;

import cn.xinghuaerp.open.wei.vo.BaseResVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class AccountGetVo extends BaseResVo {
    private Integer total_num;
    private List<AccountVo> account_list;
}
