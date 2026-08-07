package cn.xinghuaerp.purchase.request;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SupplierDispatchRequest {
    private List<Long> ids;
    private Long supplierId;
    private Long warehouseId;
    /** 明细ID到本次分配数量；未提供时分配剩余数量。 */
    private Map<String, Integer> quantities;
}
