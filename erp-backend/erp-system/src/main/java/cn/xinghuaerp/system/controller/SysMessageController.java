package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.notify.NotifierService;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.service.ISysMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/sys/message")
@RequiredArgsConstructor
public class SysMessageController {

    /**
     * 手动重试的墙钟预算（毫秒）。
     *
     * <p>取值理由：60s 是「前端一次按钮点击可接受的等待上限」与「单条消息最坏耗时」的折中。
     * 单条消息要串行推所有启用渠道，单渠道一次 HTTP 最坏占用 callTimeout(30s)，
     * 20 条消息串行下来最坏可以是几十分钟；同步接口不能把 Tomcat 工作线程占那么久。</p>
     *
     * <p><b>它不是请求耗时的硬上界</b>：预算只决定「不再发起新的投递」，已经在途的那一条仍会跑完，
     * 所以单次请求最坏耗时是 60s + 单条最坏耗时（单渠道 callTimeout 30s × 启用渠道数），
     * 而不是 60s。未处理的消息保持原状态，交给 10 分钟超时窗口的定时补偿继续推进。</p>
     */
    private static final long RETRY_NOTIFY_BUDGET_MILLIS = 60_000L;

    private final ISysMessageService messageService;
    private final NotifierService notifierService;

    /** 单次手动重试的墙钟预算，包级可见并允许子类覆盖，便于用毫秒级预算验证超预算行为。 */
    long retryNotifyBudgetMillis() {
        return RETRY_NOTIFY_BUDGET_MILLIS;
    }

    @GetMapping("/unread")
    public AjaxResult getUnread() {
        return AjaxResult.success(messageService.getUnread());
    }

    @GetMapping("/count")
    public AjaxResult countUnread() {
        return AjaxResult.success(messageService.countUnread());
    }

    @PostMapping("/read/{id}")
    public AjaxResult markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return AjaxResult.success();
    }

    @PostMapping("/read-all")
    public AjaxResult markAllRead() {
        messageService.markAllRead();
        return AjaxResult.success();
    }

    @PostMapping("/retry-notify")
    public AjaxResult retryNotify() {
        List<SysMessage> failed = messageService.getFailedNotify();
        long deadline = System.currentTimeMillis() + retryNotifyBudgetMillis();
        int success = 0;
        int failure = 0;
        int skipped = 0;
        for (SysMessage msg : failed) {
            // 墙钟预算：预算耗尽后不再发起新的一次投递，剩余消息原样留给定时补偿（不改状态、不回滚）。
            // 尚未抢占的消息状态仍是 0/2/3，全部落在 getFailedNotify 的补偿窗口内，下一轮会被重新捡起。
            if (System.currentTimeMillis() >= deadline) {
                skipped++;
                continue;
            }
            // 与定时补偿共用同一把幂等闸门：抢不到投递权说明别的实例/线程正在发送，跳过而不是当失败。
            if (!messageService.claimNotify(msg.getId(), msg.getNotifyStatus())) {
                skipped++;
                continue;
            }
            boolean allOk = notifierService.notifyAll(msg.getTitle(), msg.getContent());
            // 条件结算本次投递，只写 notify_status/notify_time：推送期间用户标记已读的结果不能被覆盖。
            messageService.finishNotify(msg.getId(), allOk ? 1 : 2);
            if (allOk) {
                success++;
            } else {
                failure++;
            }
        }
        return AjaxResult.success("重试完成，成功 " + success + " 条，失败 " + failure
                + " 条，跳过 " + skipped + " 条");
    }
}
