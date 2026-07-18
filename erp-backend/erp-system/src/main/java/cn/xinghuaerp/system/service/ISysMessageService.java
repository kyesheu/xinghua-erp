package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.entity.SysMessage;

import java.util.List;

public interface ISysMessageService {
    void save(SysMessage msg);
    List<SysMessage> getUnread();
    long countUnread();
    void markRead(Long id);
    void markAllRead();
    List<SysMessage> getFailedNotify();
    /** 按标题判断消息是否已存在，供 MQ 重复投递的幂等去重。 */
    boolean existsByTitle(String title);
}
