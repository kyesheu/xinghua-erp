package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.entity.SysMessage;

import java.util.List;

public interface ISysMessageService {

    /**
     * 保存一条消息。
     *
     * <p><b>仅用于新建（{@code id == null}）。</b>更新已有消息的通知状态必须用
     * {@link #finishNotify(Long, int)}：本方法在 id 非空时按主键整实体回写，
     * 会把调用方读取时的旧快照（未读标记、阅读时间、标题、内容、去重键等）覆盖回去，
     * 造成并发修改丢失。</p>
     *
     * @param msg 待落库的消息实体
     */
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
     * @param expectedNotifyStatus 读取批次时看到的通知状态，用于乐观并发控制；
     *                             传 {@code null} 表示从「待推送」抢占（刚插入的新消息）
     * @return 抢到投递权返回 {@code true}
     */
    boolean claimNotify(Long id, Integer expectedNotifyStatus);

    /**
     * 结算一次通知投递结果。
     *
     * <p>实现必须是<b>针对性条件更新</b>：只写 {@code notify_status} 与 {@code notify_time}，
     * 并以 {@code notify_status = 发送中(3)} 作为围栏。因此：</p>
     * <ul>
     *   <li>投递期间用户「标记已读」、编辑标题等并发修改不会被覆盖（无 lost update）；</li>
     *   <li>租约已被别的实例接管时影响行数为 0，返回 {@code false}，不会覆盖对方的结算结果。</li>
     * </ul>
     *
     * @param id           消息ID
     * @param notifyStatus 本次投递的结算状态（1-已推送 2-推送失败）
     * @return 结算是否生效（{@code false} 表示租约已失效，调用方只需记日志，不应中断整批）
     */
    boolean finishNotify(Long id, int notifyStatus);

    /** 按标题判断消息是否已存在，供 MQ 重复投递的幂等去重。 */
    boolean existsByTitle(String title);
}
