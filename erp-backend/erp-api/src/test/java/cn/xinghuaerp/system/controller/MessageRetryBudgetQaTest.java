package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.notify.NotifierService;
import cn.xinghuaerp.system.service.ISysMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QA 独立复核：手动重试接口的 60s 墙钟预算边界。
 *
 * <p>关注三件事：</p>
 * <ol>
 *   <li>预算检查必须在 {@code claimNotify} <b>之前</b>——超预算的消息一个字都不能改，
 *       否则它会被推到「发送中(3)」却没人结算，只能等 10 分钟超时窗口；</li>
 *   <li>成功/失败/跳过三类计数之和必须等于本批条数，不能有「统计黑洞」；</li>
 *   <li>预算在投递中途耗尽时，<b>已经抢占的那一条必须被结算</b>，不能留下 3 的残留。</li>
 * </ol>
 */
class MessageRetryBudgetQaTest {

    @Test
    @DisplayName("预算为 0：一条都不抢占、一条都不推，全部计入跳过")
    void zeroBudgetClaimsNothing() {
        ISysMessageService messages = mock(ISysMessageService.class);
        NotifierService notifier = mock(NotifierService.class);
        when(messages.getFailedNotify()).thenReturn(List.of(message(1L, 0), message(2L, 2)));

        AjaxResult result = controller(messages, notifier, 0L).retryNotify();

        verify(messages, never()).claimNotify(anyLong(), any());
        verify(notifier, never()).notifyAll(any(), any());
        verify(messages, never()).finishNotify(anyLong(), anyInt(), any());
        String msg = String.valueOf(result.get("msg"));
        assertTrue(msg.contains("成功 0 条") && msg.contains("失败 0 条") && msg.contains("跳过 2 条"), msg);
    }

    @Test
    @DisplayName("预算在投递中途耗尽：已抢占的那条仍必须结算，不留 notify_status=3 残留")
    void messageClaimedBeforeDeadlineIsAlwaysSettled() {
        ISysMessageService messages = mock(ISysMessageService.class);
        NotifierService notifier = mock(NotifierService.class);
        when(messages.getFailedNotify()).thenReturn(List.of(message(1L, 0), message(2L, 0)));
        when(messages.claimNotify(anyLong(), any())).thenAnswer(call -> "lease-" + call.getArgument(0));
        // 第一条投递耗时远超预算：预算在 notifyAll 内部就已耗尽
        when(notifier.notifyAll(eq("标题1"), any())).thenAnswer(call -> {
            Thread.sleep(800L);
            return true;
        });

        // 预算 500ms < 单次投递 800ms：第一条一定在「预算尚未耗尽」时被抢占，投递期间预算耗尽。
        AjaxResult result = controller(messages, notifier, 500L).retryNotify();

        verify(messages, times(1)).claimNotify(1L, 0);
        verify(messages, times(1)).finishNotify(1L, 1, "lease-1");
        verify(messages, never()).claimNotify(2L, 0);
        String msg = String.valueOf(result.get("msg"));
        assertTrue(msg.contains("成功 1 条") && msg.contains("跳过 1 条"), msg);
    }

    @Test
    @DisplayName("抢占用的是读取时看到的 notify_status（乐观并发控制不能被改成 null）")
    void claimsWithStatusSeenWhenReadingBatch() {
        ISysMessageService messages = mock(ISysMessageService.class);
        NotifierService notifier = mock(NotifierService.class);
        when(messages.getFailedNotify()).thenReturn(List.of(message(1L, 0), message(2L, 2), message(3L, 3)));
        when(messages.claimNotify(anyLong(), any())).thenReturn(null);

        controller(messages, notifier, 60_000L).retryNotify();

        verify(messages).claimNotify(1L, 0);
        verify(messages).claimNotify(2L, 2);
        verify(messages).claimNotify(3L, 3);
    }

    @Test
    @DisplayName("成功 + 失败 + 被抢走 三类混合时，计数之和等于批次大小")
    void countersAddUpForMixedDeliveries() {
        ISysMessageService messages = mock(ISysMessageService.class);
        NotifierService notifier = mock(NotifierService.class);
        List<SysMessage> batch = List.of(message(1L, 0), message(2L, 0), message(3L, 0));
        when(messages.getFailedNotify()).thenReturn(batch);
        // 1 成功推送、2 通道返回失败、3 被别的实例抢走
        when(messages.claimNotify(1L, 0)).thenReturn("lease-1");
        when(messages.claimNotify(2L, 0)).thenReturn("lease-2");
        when(messages.claimNotify(3L, 0)).thenReturn(null);
        when(notifier.notifyAll("标题1", "内容1")).thenReturn(true);
        when(notifier.notifyAll("标题2", "内容2")).thenReturn(false);

        AjaxResult result = controller(messages, notifier, 60_000L).retryNotify();

        String msg = String.valueOf(result.get("msg"));
        int success = numberAfter(msg, "成功 ");
        int failure = numberAfter(msg, "失败 ");
        int skipped = numberAfter(msg, "跳过 ");
        assertEquals(batch.size(), success + failure + skipped, msg);
        assertEquals(1, success, msg);
        assertEquals(1, failure, msg);
        assertEquals(1, skipped, msg);
        verify(messages).finishNotify(1L, 1, "lease-1");
        verify(messages).finishNotify(2L, 2, "lease-2");
    }

    @Test
    @DisplayName("空批次不产生任何调用，计数全 0")
    void emptyBatchIsHarmless() {
        ISysMessageService messages = mock(ISysMessageService.class);
        NotifierService notifier = mock(NotifierService.class);
        when(messages.getFailedNotify()).thenReturn(List.of());

        AjaxResult result = controller(messages, notifier, 60_000L).retryNotify();

        verify(messages, never()).claimNotify(anyLong(), any());
        String msg = String.valueOf(result.get("msg"));
        assertNotNull(msg);
        assertTrue(msg.contains("成功 0 条") && msg.contains("失败 0 条") && msg.contains("跳过 0 条"), msg);
    }

    /**
     * 残留路径（如实钉住，不是对缺陷的认可）：
     *
     * <p>抢占之后如果 {@code notifyAll} 抛了未捕获异常（例如 {@code channelService.list} 连不上库），
     * 这条消息会停在 {@code notify_status=3} 没人结算，接口直接 500。
     * 注意 {@code NotifierService.notifyAll} 自己会吞掉每个渠道的异常，所以只有「取渠道列表」这类
     * 渠道之外的动作才会走到这里。残留可被 {@code getFailedNotify} 的 10 分钟超时窗口捡回
     * （该窗口把 3 也算作可补偿），因此判定为可接受。</p>
     */
    @Test
    @DisplayName("投递抛未捕获异常时：接口异常上抛，消息停在 3（由 10 分钟超时窗口捡回）")
    void uncaughtDeliveryFailureLeavesSendingStatusForTimeoutWindow() {
        ISysMessageService messages = mock(ISysMessageService.class);
        NotifierService notifier = mock(NotifierService.class);
        when(messages.getFailedNotify()).thenReturn(List.of(message(1L, 0)));
        when(messages.claimNotify(1L, 0)).thenReturn("lease-1");
        when(notifier.notifyAll(any(), any())).thenThrow(new IllegalStateException("渠道列表读取失败"));

        assertThrows(IllegalStateException.class, () -> controller(messages, notifier, 60_000L).retryNotify());

        verify(messages).claimNotify(1L, 0);
        verify(messages, never()).finishNotify(anyLong(), anyInt(), any());
    }

    private SysMessageController controller(ISysMessageService messages, NotifierService notifier, long budget) {
        return new SysMessageController(messages, notifier) {
            @Override
            long retryNotifyBudgetMillis() {
                return budget;
            }
        };
    }

    private SysMessage message(long id, int notifyStatus) {
        SysMessage msg = new SysMessage();
        msg.setId(id);
        msg.setTitle("标题" + id);
        msg.setContent("内容" + id);
        msg.setNeedNotify(1);
        msg.setNotifyStatus(notifyStatus);
        return msg;
    }

    private int numberAfter(String text, String marker) {
        int start = text.indexOf(marker);
        assertTrue(start >= 0, "返回值必须包含 " + marker + "：" + text);
        int cursor = start + marker.length();
        int end = cursor;
        while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
        return Integer.parseInt(text.substring(cursor, end));
    }
}
