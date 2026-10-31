package cn.xinghuaerp.erp.task;

import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import cn.xinghuaerp.order.service.OOrderService;
import cn.xinghuaerp.order.service.ShopRefundService;
import cn.xinghuaerp.product.service.OGoodsSkuService;
import cn.xinghuaerp.sse.SseService;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.notify.NotifierService;
import cn.xinghuaerp.system.service.ISysMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QA 独立复核：{@code MessageScheduler.save()} 里「新建消息」的投递链路。
 *
 * <p>改动前：插入后直接推送，然后对同一个实体调 {@code save(msg)} → 整实体回写，
 * 会把推送期间用户写入的 {@code is_read} 覆盖回 0。</p>
 *
 * <p>改动后：插入 → {@code claimNotify(id, null)} 抢占（0→3）→ 推送 → {@code finishNotify}。
 * 本用例用反射调用私有 {@code save(...)}，因为 {@code run()} 里的 check* 调用目前全部被注释掉，
 * 这条链路在生产上暂时无可达入口，只能直接钉住实现。</p>
 */
class MessageSchedulerSaveMessageQaTest {

    private final ISysMessageService messageService = mock(ISysMessageService.class);
    private final NotifierService notifier = mock(NotifierService.class);
    private final SseService sseService = mock(SseService.class);
    private final MessageScheduler scheduler = new MessageScheduler(
            mock(OOrderService.class), mock(ShopRefundService.class), messageService, notifier, sseService,
            mock(OGoodsInventoryService.class), mock(OGoodsSkuService.class));

    @Test
    @DisplayName("新消息：插入 → 抢占 → 推送 → 条件结算，全程不再对 id 非空实体调 save")
    void newMessageIsClaimedBeforeDeliveryAndSettledConditionally() throws Exception {
        stubInsertAssignsId(88L);
        when(messageService.claimNotify(88L, null)).thenReturn("lease-88");
        when(notifier.notifyAll("库存不足", "SKU 只剩 1 件")).thenReturn(true);

        invokeSave("stock_low", "high", "库存不足", "SKU 只剩 1 件");

        InOrder order = inOrder(messageService, notifier);
        order.verify(messageService).save(any(SysMessage.class));
        order.verify(messageService).claimNotify(88L, null);
        order.verify(notifier).notifyAll("库存不足", "SKU 只剩 1 件");
        order.verify(messageService).finishNotify(88L, 1, "lease-88");

        // 插入之后不得再对同一个实体整实体回写（原缺陷）：
        // 整条链路只能有一次 save，而且那一次必须发生在 id 还没生成的时候。
        verify(messageService, times(1)).save(any(SysMessage.class));
        assertTrue(idWasNullWhenSaved, "唯一一次 save 必须是插入（调用时 id 为 null），否则就是整实体回写");
        assertEquals(0, notifyStatusWhenSaved, "插入时状态应为待推送");
    }

    @Test
    @DisplayName("抢占失败（别的实例正在发）：不推送、不结算，消息留在待推送等补偿窗口")
    void failedClaimSkipsDeliveryEntirely() throws Exception {
        stubInsertAssignsId(88L);
        when(messageService.claimNotify(88L, null)).thenReturn(null);

        invokeSave("stock_low", "high", "库存不足", "SKU 只剩 1 件");

        verify(notifier, never()).notifyAll(any(), any());
        verify(messageService, never()).finishNotify(any(), anyInt(), any());
        verify(messageService, times(1)).save(any(SysMessage.class));
        verify(sseService).broadcastMessage(eq("message"), any());
    }

    @Test
    @DisplayName("通道全部失败：结算为 2（推送失败），不写 is_read 之类的业务字段")
    void deliveryFailureSettlesAsFailed() throws Exception {
        stubInsertAssignsId(88L);
        when(messageService.claimNotify(88L, null)).thenReturn("lease-88");
        when(notifier.notifyAll(any(), any())).thenReturn(false);

        invokeSave("stock_low", "high", "库存不足", "SKU 只剩 1 件");

        verify(messageService).finishNotify(88L, 2, "lease-88");
        verify(messageService, times(1)).save(any(SysMessage.class));
    }

    @Test
    @DisplayName("不需要外部通知的消息（low 级别、非通知类型）完全不进投递链路")
    void messageWithoutNotifyNeedSkipsDelivery() throws Exception {
        stubInsertAssignsId(88L);

        invokeSave("sales_zero", "low", "今日销售额为零", "今日暂无销售额");

        verify(messageService, never()).claimNotify(any(), any());
        verify(notifier, never()).notifyAll(any(), any());
        verify(messageService, never()).finishNotify(any(), anyInt(), any());
        verify(messageService, times(1)).save(any(SysMessage.class));
    }

    /** save 被调用那一刻实体长什么样——调用后 id 会被回填，事后再看是看不到真相的。 */
    private boolean idWasNullWhenSaved;
    private int notifyStatusWhenSaved = -1;

    private void stubInsertAssignsId(long id) {
        doAnswer(call -> {
            SysMessage msg = call.getArgument(0);
            idWasNullWhenSaved = msg.getId() == null;
            notifyStatusWhenSaved = msg.getNotifyStatus() == null ? -1 : msg.getNotifyStatus();
            msg.setId(id);
            return null;
        }).when(messageService).save(any(SysMessage.class));
    }

    private void invokeSave(String type, String level, String title, String content) throws Exception {
        Method save = MessageScheduler.class.getDeclaredMethod(
                "save", String.class, String.class, String.class, String.class, String.class, String.class);
        save.setAccessible(true);
        save.invoke(scheduler, type, level, title, content, "/sale/shop_order_list", "system");
    }
}
