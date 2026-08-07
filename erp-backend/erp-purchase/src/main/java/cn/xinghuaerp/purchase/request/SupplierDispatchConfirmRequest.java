package cn.xinghuaerp.purchase.request;

import lombok.Data;

@Data
public class SupplierDispatchConfirmRequest {
    private Long id;
    private Long logisticsCompany;
    private String waybillCode;
}
