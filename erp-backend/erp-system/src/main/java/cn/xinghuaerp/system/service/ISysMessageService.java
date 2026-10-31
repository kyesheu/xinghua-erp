package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.entity.SysMessage;

import java.util.List;

public interface ISysMessageService {

    /**
     * 保存一条消息。
     *
     * <p><b>仅用于新建（{@code id == null}）。</b>更新已有消息的通知状态必须用
     * {@link #finishNotify(Long, int, String)}：本方法在 id 非空时按主键整实体回写，
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
     * 影响行数，只有抢到（返回值非 {@code null}）的实例才允许调用通知渠道，
     * 否则同一个 messageId 会被多个实例重复投递。</p>
     *
     * <p>返回值是一次性<b>租约令牌</b>（写入 {@code sys_message.notify_token}），调用方必须在
     * {@link #finishNotify(Long, int, String)} 时原样带回。光靠「当前是发送中」这个状态做围栏是不够的：
     * 租约超过补偿窗口被别的实例接管后，状态仍然是发送中，过期的持有者照样能结算，
     * 于是它那份早已过时的投递结果会覆盖接管者的真实结果（先到者赢）。带上令牌后，
     * 只有当前令牌的持有者能结算，接管者写新令牌即让旧持有者的结算影响行数归零。</p>
     *
     * @param id                   消息ID
     * @param expectedNotifyStatus 读取批次时看到的通知状态，用于乐观并发控制；
     *                             传 {@code null} 表示从「待推送」抢占（刚插入的新消息）
     * @return 抢到投递权时返回本次租约令牌；没抢到返回 {@code null}
     */
    String claimNotify(Long id, Integer expectedNotifyStatus);

    /**
     * 结算一次通知投递结果。
     *
     * <p>实现必须是<b>针对性条件更新</b>：只写 {@code notify_status}、{@code notify_time} 并清空
     * {@code notify_token}，围栏为 {@code notify_status = 发送中(3) AND notify_token = 本次令牌}。因此：</p>
     * <ul>
     *   <li>投递期间用户「标记已读」、编辑标题等并发修改不会被覆盖（无 lost update）；</li>
     *   <li>租约已被别的实例接管（令牌已被换掉）时影响行数为 0，返回 {@code false}，
     *       过期持有者的旧结果不会覆盖接管者的真实结果。</li>
     * </ul>
     *
     * @param id           消息ID
     * @param notifyStatus 本次投递的结算状态（1-已推送 2-推送失败）
     * @param token        {@link #claimNotify} 返回的租约令牌
     * @return 结算是否生效（{@code false} 表示租约已失效，调用方只需记日志，不应中断整批）
     */
    boolean finishNotify(Long id, int notifyStatus, String token);

    /** 按标题判断消息是否已存在，供 MQ 重复投递的幂等去重。 */
    boolean existsByTitle(String title);

    /**
     * 按去重键判断消息是否已存在。写库时 {@code dedup_key} 有唯一索引，这里用于
     * 提前短路（例如定时预警「一天一条」），避免每轮都把数据查一遍再撞索引。
     */
    boolean existsByDedupKey(String dedupKey);
}
