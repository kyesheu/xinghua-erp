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

    /**
     * 抢占一条待补偿消息的投递权，作为多实例并发下的幂等闸门。
     *
     * <p>实现必须基于数据库条件更新（{@code UPDATE ... WHERE id=? AND notify_status=旧值}）并返回
     * 影响行数，只有抢到的实例（返回 {@code true}）才允许调用通知渠道，
     * 否则同一个 messageId 会被多个实例重复投递。</p>
     *
     * @param id                   消息ID
     * @param expectedNotifyStatus 读取批次时看到的通知状态，用于乐观并发控制
     * @return 抢到投递权返回 {@code true}
     */
    boolean claimNotify(Long id, Integer expectedNotifyStatus);

    /** 按标题判断消息是否已存在，供 MQ 重复投递的幂等去重。 */
    boolean existsByTitle(String title);
}
