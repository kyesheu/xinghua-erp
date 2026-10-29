package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.notify.NotifierService;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.service.ISysMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/sys/message")
@RequiredArgsConstructor
public class SysMessageController {

    private final ISysMessageService messageService;
    private final NotifierService notifierService;

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
        int success = 0;
        for (SysMessage msg : failed) {
            // 与定时补偿共用同一把幂等闸门：抢不到投递权说明别的实例/线程正在发送，直接跳过。
            if (!messageService.claimNotify(msg.getId(), msg.getNotifyStatus())) continue;
            boolean allOk = notifierService.notifyAll(msg.getTitle(), msg.getContent());
            msg.setNotifyStatus(allOk ? 1 : 2);
            msg.setNotifyTime(LocalDateTime.now());
            messageService.save(msg);
            if (allOk) success++;
        }
        return AjaxResult.success("重试完成，成功 " + success + "/" + failed.size() + " 条");
    }
}
