package cn.xinghuaerp.inventory.request;

import lombok.Data;

@Data
public class SupplierShareMerchantRequest {
    private Long id;//供应商Id
    private Long[] merchantIds;//商户ID
}
