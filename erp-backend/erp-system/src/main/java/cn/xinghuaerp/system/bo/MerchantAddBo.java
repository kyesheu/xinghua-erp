package cn.xinghuaerp.system.bo;

import lombok.Data;

@Data
public class MerchantAddBo {
    private Long id;
    private String name;
    private String number;
    private String usci;
    private String faren;
    private String linkMan;
    private String mobile;
    private String address;
    private String loginName;
    private String loginPwd;
    private String remark;
    /** 0启用，1停用；未传时默认启用。 */
    private String status;

}
