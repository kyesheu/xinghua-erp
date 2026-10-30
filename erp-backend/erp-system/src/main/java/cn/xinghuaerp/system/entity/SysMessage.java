package cn.xinghuaerp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_message")
public class SysMessage {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String dedupKey;
    private String type;
    private String level;
    private String title;
    private String content;
    private String link;
    private String source;
    private Integer isRead;
    private Integer needNotify;
    private Integer notifyStatus;
    private LocalDateTime notifyTime;
    /** 通知投递的租约令牌：claim 时写入，结算时按令牌匹配，用于区分“这次发送中是不是自己抢的”。 */
    private String notifyToken;
    private LocalDateTime createdTime;
    private LocalDateTime readTime;
}
