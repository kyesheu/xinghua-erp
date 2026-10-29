package cn.xinghuaerp.system.notify;

import cn.xinghuaerp.system.entity.SysAlertChannel;

/** 外部通知策略，注册时要求渠道类型唯一。 */
public interface ChannelNotifier {
    String type();
    boolean send(SysAlertChannel channel,String title,String content);
}
