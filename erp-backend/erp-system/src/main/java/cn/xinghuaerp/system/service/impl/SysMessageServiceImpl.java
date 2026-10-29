package cn.xinghuaerp.system.service.impl;

import cn.xinghuaerp.system.mapper.SysMessageMapper;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.service.ISysMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysMessageServiceImpl implements ISysMessageService {

    /** 通知状态：待推送。 */
    public static final int NOTIFY_STATUS_PENDING = 0;
    /** 通知状态：发送中（已被某个实例抢占，其他实例不得重复投递）。 */
    public static final int NOTIFY_STATUS_SENDING = 3;

    private final SysMessageMapper mapper;

    /**
     * 保存一条消息。<b>仅用于新建（{@code id == null}）。</b>
     *
     * <p>已有消息的通知状态结算必须走 {@link #finishNotify(Long, int)}：本方法在 id 非空时
     * 按主键整实体回写（{@code updateById}，默认 NOT_NULL 策略），会把调用方读取时的旧快照里
     * 所有非空字段（{@code is_read}、{@code title}、{@code content}、{@code dedup_key}、
     * {@code created_time} …）全部覆盖回数据库。通知推送是阻塞数秒到数十秒的调用，
     * 期间用户「标记已读」的结果会被整实体回写改回未读，是典型的 lost update。</p>
     */
    @Override
    public void save(SysMessage msg) {
        if (msg.getCreatedTime() == null) {
            msg.setCreatedTime(LocalDateTime.now());
        }
        if (msg.getId() != null) {
            mapper.updateById(msg);
        } else {
            try {
                if (mapper.insert(msg) != 1) throw new IllegalStateException("消息保存失败");
            } catch (org.springframework.dao.DuplicateKeyException e) {
                if (msg.getDedupKey() == null || mapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                        .eq(SysMessage::getDedupKey, msg.getDedupKey())) == 0) throw e;
            }
        }
    }

    @Override
    public List<SysMessage> getUnread() {
        LambdaQueryWrapper<SysMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysMessage::getIsRead, 0);
        w.orderByDesc(SysMessage::getCreatedTime, SysMessage::getId);
        w.last("LIMIT 100");
        return mapper.selectList(w);
    }

    @Override
    public long countUnread() {
        LambdaQueryWrapper<SysMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysMessage::getIsRead, 0);
        return mapper.selectCount(w);
    }

    @Override
    public boolean existsByTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<SysMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysMessage::getTitle, title);
        return mapper.selectCount(w) > 0;
    }

    @Override
    public void markRead(Long id) {
        if (id == null) {
            return;
        }
        // 只条件更新已读相关的两列，绝不能「selectById 再 updateById」整实体回写：
        // 回写会把读到那一刻的 notify_status 一起写回去——用户点已读时读到 notify_status=0，
        // 期间补偿任务把状态推成 3 并结算成 1，整实体回写就把 0 写回，于是这条消息重新落进
        // getFailedNotify 的 (0,2,3) 补偿窗口，被再投递一次（已读操作造成重复通知）。
        mapper.update(null, new UpdateWrapper<SysMessage>()
                .eq("id", id)
                .set("is_read", 1)
                .set("read_time", LocalDateTime.now()));
    }

    @Override
    public List<SysMessage> getFailedNotify() {
        LambdaQueryWrapper<SysMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysMessage::getNeedNotify, 1);
        var cutoff=LocalDateTime.now().minusMinutes(10);
        // 0-待推送 2-推送失败 3-发送中（被某实例抢占）。3 也纳入重试窗口，
        // 这样实例崩溃导致的“发送中”残留会在超时后回到可补偿状态，不会永久卡住。
        w.in(SysMessage::getNotifyStatus, 0, 2, NOTIFY_STATUS_SENDING);
        w.and(query->query.lt(SysMessage::getNotifyTime,cutoff)
                .or(pending->pending.isNull(SysMessage::getNotifyTime).lt(SysMessage::getCreatedTime,cutoff)));
        w.orderByAsc(SysMessage::getNotifyTime);
        w.last("LIMIT 20");
        return mapper.selectList(w);
    }

    @Override
    public boolean claimNotify(Long id, Integer expectedNotifyStatus) {
        if (id == null) {
            return false;
        }
        // 条件更新即幂等闸门：只有把状态从“读取时看到的值”改成“发送中”的那一次才算抢到，
        // 多实例并发时另一实例的影响行数为 0，直接跳过，不会重复触达通知渠道。
        int expected = expectedNotifyStatus == null ? NOTIFY_STATUS_PENDING : expectedNotifyStatus;
        return mapper.update(null, new UpdateWrapper<SysMessage>()
                .eq("id", id)
                .eq("notify_status", expected)
                .set("notify_status", NOTIFY_STATUS_SENDING)
                .set("notify_time", LocalDateTime.now())) == 1;
    }

    @Override
    public boolean finishNotify(Long id, int notifyStatus) {
        if (id == null) {
            return false;
        }
        // 只结算本次投递：条件更新只 set 两个投递相关列，并带 notify_status = 发送中 的围栏。
        // 这样既不会覆盖投递期间用户并发写入的 is_read/read_time/title，
        // 也不会覆盖别人接管租约后写下的结算结果（影响行数为 0）。
        int rows = mapper.update(null, new UpdateWrapper<SysMessage>()
                .eq("id", id)
                .eq("notify_status", NOTIFY_STATUS_SENDING)
                .set("notify_status", notifyStatus)
                .set("notify_time", LocalDateTime.now()));
        if (rows == 1) {
            return true;
        }
        log.warn("通知结果结算未生效（租约已被接管或已结算） id={} target={} current={}",
                id, notifyStatus, currentNotifyStatus(id));
        return false;
    }

    /** 结算失败时读一次现状，仅用于日志定位；读不到或读取异常都不影响主流程。 */
    private Object currentNotifyStatus(Long id) {
        try {
            SysMessage current = mapper.selectById(id);
            return current == null ? "记录不存在" : current.getNotifyStatus();
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    @Override
    public void markAllRead() {
        LambdaQueryWrapper<SysMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysMessage::getIsRead, 0);
        SysMessage update = new SysMessage();
        update.setIsRead(1);
        update.setReadTime(LocalDateTime.now());
        mapper.update(update, w);
    }
}
