package cn.xinghuaerp.system.query;

import lombok.Data;

@Data
public class MerchantQuery {
    private String name;
    private String number;
    private String usci;
    private String mobile;
    private String linkMan;
    private Long merchantId;
    private Long shopId;
    private Integer status;
}
