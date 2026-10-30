package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.notify.NotifierService;
import cn.xinghuaerp.system.service.ISysMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 手动重试接口的墙钟预算验证。
 *
 * <p>预算 500ms、每条消息的投递阻塞 800ms：第一条跑完即超预算，剩余两条必须原样留给
 * 10 分钟超时窗口的定时补偿，返回值里的「跳过」数要如实统计。
 * 这是「一个 HTTP 请求不能占住 Tomcat 工作线程几十分钟」的容量约束。</p>
 *
 * <p>预算/睡眠取 500ms/800ms 而不是 100ms/250ms：后者在机器负载或 GC 抖动时，
 * 第一条可能在进入循环后的第一次预算检查前就已超时，造成偶发红灯。</p>
 */
class MessageRetryBudgetTest {

    private static final long TEST_BUDGET_MILLIS = 500L;
    private static final long ONE_DELIVERY_MILLIS = 800L;

    @Test
    @DisplayName("超预算后停止投递，剩余消息原样留给定时补偿")
    void stopsDispatchingRemainingMessagesOnceBudgetIsExhausted() {
        var messages = mock(ISysMessageService.class);
        var notifier = mock(NotifierService.class);
        when(messages.getFailedNotify()).thenReturn(List.of(message(1L, "标题1"), message(2L, "标题2"), message(3L, "标题3")));
        when(messages.claimNotify(anyLong(), any())).thenAnswer(call -> "lease-" + call.getArgument(0));
        when(notifier.notifyAll(any(), any())).thenAnswer(call -> {
            Thread.sleep(ONE_DELIVERY_MILLIS);
            return true;
        });

        AjaxResult result = controllerWithBudget(messages, notifier).retryNotify();

        // 只推了第一条，后两条连抢占都没做（保持 0/2/3，仍在补偿窗口内）
        verify(notifier, times(1)).notifyAll("标题1", "内容1");
        verify(messages, times(1)).claimNotify(anyLong(), any());
        verify(messages).finishNotify(1L, 1, "lease-1");
        verify(messages, never()).claimNotify(eq(2L), any());
        verify(messages, never()).claimNotify(eq(3L), any());
        verify(messages, never()).finishNotify(eq(2L), anyInt(), any());
        verify(messages, never()).finishNotify(eq(3L), anyInt(), any());

        String message = String.valueOf(result.get("msg"));
        assertTrue(message.contains("成功 1 条"), message);
        assertTrue(message.contains("失败 0 条"), message);
        assertTrue(message.contains("跳过 2 条"), message);
    }

    @Test
    @DisplayName("抢占失败计入跳过，不再被当成失败条数")
    void messagesClaimedByAnotherInstanceCountAsSkipped() {
        var messages = mock(ISysMessageService.class);
        var notifier = mock(NotifierService.class);
        when(messages.getFailedNotify()).thenReturn(List.of(message(1L, "标题1"), message(2L, "标题2")));
        // 两条都被别的实例/线程抢走
        when(messages.claimNotify(anyLong(), any())).thenReturn(null);

        AjaxResult result = controllerWithBudget(messages, notifier).retryNotify();

        verify(notifier, never()).notifyAll(any(), any());
        verify(messages, never()).finishNotify(anyLong(), anyInt(), any());
        String message = String.valueOf(result.get("msg"));
        assertTrue(message.contains("成功 0 条"), message);
        assertTrue(message.contains("失败 0 条"), message);
        assertTrue(message.contains("跳过 2 条"), message);
    }

    @Test
    @DisplayName("通道返回失败时计入失败条数，并如实结算为推送失败")
    void failedChannelsAreCountedAsFailures() {
        var messages = mock(ISysMessageService.class);
        var notifier = mock(NotifierService.class);
        when(messages.getFailedNotify()).thenReturn(List.of(message(1L, "标题1")));
        when(messages.claimNotify(anyLong(), any())).thenAnswer(call -> "lease-" + call.getArgument(0));
        when(notifier.notifyAll(any(), any())).thenReturn(false);

        AjaxResult result = controllerWithBudget(messages, notifier).retryNotify();

        verify(messages).finishNotify(1L, 2, "lease-1");
        String message = String.valueOf(result.get("msg"));
        assertTrue(message.contains("成功 0 条"), message);
        assertTrue(message.contains("失败 1 条"), message);
        assertTrue(message.contains("跳过 0 条"), message);
    }

    /** 用子类把墙钟预算压到毫秒级，避免测试真的等 60s。 */
    private SysMessageController controllerWithBudget(ISysMessageService messages, NotifierService notifier) {
        return new SysMessageController(messages, notifier) {
            @Override
            long retryNotifyBudgetMillis() {
                return TEST_BUDGET_MILLIS;
            }
        };
    }

    private SysMessage message(long id, String title) {
        var msg = new SysMessage();
        msg.setId(id);
        msg.setTitle(title);
        msg.setContent("内容" + id);
        msg.setNeedNotify(1);
        msg.setNotifyStatus(0);
        return msg;
    }

    /** 防御性断言：成功/失败/跳过三类之和等于读取到的批次大小。 */
    @Test
    @DisplayName("成功/失败/跳过三类之和等于批次大小")
    void countersAddUpToBatchSize() {
        var messages = mock(ISysMessageService.class);
        var notifier = mock(NotifierService.class);
        when(messages.getFailedNotify()).thenReturn(List.of(message(1L, "标题1"), message(2L, "标题2")));
        when(messages.claimNotify(anyLong(), any())).thenAnswer(call -> "lease-" + call.getArgument(0));
        when(notifier.notifyAll(any(), any())).thenReturn(true);

        AjaxResult result = controllerWithBudget(messages, notifier).retryNotify();

        String message = String.valueOf(result.get("msg"));
        int success = numberAfter(message, "成功 ");
        int failure = numberAfter(message, "失败 ");
        int skipped = numberAfter(message, "跳过 ");
        assertEquals(2, success + failure + skipped, message);
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
