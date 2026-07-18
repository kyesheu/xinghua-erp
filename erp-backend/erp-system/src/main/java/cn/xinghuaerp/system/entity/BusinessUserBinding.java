package cn.xinghuaerp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 账号与业务主体的显式绑定；与组织部门无关。 */
@Data
@TableName("erp_business_user_binding")
public class BusinessUserBinding {
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
    private String subjectType;
    private Long subjectId;
    private Integer enabled;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
