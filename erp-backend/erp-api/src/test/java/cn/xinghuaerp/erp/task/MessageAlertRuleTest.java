package cn.xinghuaerp.erp.task;

import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import cn.xinghuaerp.order.entity.OOrder;
import cn.xinghuaerp.order.service.OOrderService;
import cn.xinghuaerp.order.service.ShopRefundService;
import cn.xinghuaerp.product.entity.OGoodsSku;
import cn.xinghuaerp.product.service.OGoodsSkuService;
import cn.xinghuaerp.sse.SseService;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.notify.NotifierService;
import cn.xinghuaerp.system.service.ISysMessageService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时预警规则的验收用例：库存不足、发货超时、当日去重。
 *
 * <p>这两个检查此前是空方法（只有 TODO 注释），run() 里连调用都被注释掉了，
 * 整套消息通道（SSE、渠道推送、租约重试）在跑，却没有一条业务预警产出。</p>
 */
class MessageAlertRuleTest {

    private OOrderService orderService;
    private ShopRefundService refundService;
    private ISysMessageService messageService;
    private NotifierService notifierService;
    private SseService sseService;
    private OGoodsInventoryService inventoryService;
    private OGoodsSkuService goodsSkuService;
    private MessageScheduler scheduler;

    @BeforeEach
    void setUp() {
        orderService = mock(OOrderService.class);
        refundService = mock(ShopRefundService.class);
        messageService = mock(ISysMessageService.class);
        notifierService = mock(NotifierService.class);
        sseService = mock(SseService.class);
        inventoryService = mock(OGoodsInventoryService.class);
        goodsSkuService = mock(OGoodsSkuService.class);
        scheduler = new MessageScheduler(orderService, refundService, messageService, notifierService,
                sseService, inventoryService, goodsSkuService);
        // save() 落库后靠实体回填的 id 判断是否被 dedup_key 挡回，这里模拟插入成功。
        doAnswer(inv -> {
            inv.getArgument(0, SysMessage.class).setId(1L);
            return null;
        }).when(messageService).save(any(SysMessage.class));
        when(messageService.claimNotify(any(), any())).thenReturn(null);
    }

    private SysMessage invoke(String method) throws Exception {
        Method m = MessageScheduler.class.getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(scheduler);
        ArgumentCaptor<SysMessage> captor = ArgumentCaptor.forClass(SysMessage.class);
        verify(messageService).save(captor.capture());
        return captor.getValue();
    }

    private OGoodsInventory inventory(long skuId, int available, String goodsName) {
        OGoodsInventory inv = new OGoodsInventory();
        inv.setSkuId(skuId);
        inv.setAvailableQuantity(available);
        inv.setGoodsName(goodsName);
        inv.setSkuName("默认");
        inv.setIsDelete(0);
        inv.setStockStatus(1);
        return inv;
    }

    private OGoodsSku sku(String id, Integer lowQty) {
        OGoodsSku s = new OGoodsSku();
        s.setId(id);
        s.setLowQty(lowQty);
        return s;
    }

    @Test
    @DisplayName("库存低于预警线：medium，库存类属必推类型，仍走渠道")
    void belowThresholdRaisesMedium() throws Exception {
        when(inventoryService.list(any(Wrapper.class))).thenReturn(List.of(inventory(10L, 3, "银手镯")));
        when(goodsSkuService.listByIds(any())).thenReturn(List.of(sku("10", 10)));

        SysMessage msg = invoke("checkStockLow");

        assertEquals("stock_low", msg.getType());
        assertEquals("medium", msg.getLevel());
        // NEED_NOTIFY_TYPES 里本来就含 stock_low：库存告警按设计要推到渠道，不只在站内。
        assertEquals(1, msg.getNeedNotify());
        assertTrue(msg.getTitle().contains("低于预警线"), msg.getTitle());
        assertTrue(msg.getContent().contains("银手镯") && msg.getContent().contains("可用 3 件"),
                msg.getContent());
    }

    @Test
    @DisplayName("可用量为 0：升级为 high，走渠道推送")
    void outOfStockRaisesHigh() throws Exception {
        when(inventoryService.list(any(Wrapper.class))).thenReturn(List.of(inventory(10L, 0, "金项链")));
        when(goodsSkuService.listByIds(any())).thenReturn(List.of(sku("10", 5)));

        SysMessage msg = invoke("checkStockLow");

        assertEquals("high", msg.getLevel());
        assertEquals(1, msg.getNeedNotify());
        assertTrue(msg.getTitle().contains("已缺货"), msg.getTitle());
    }

    @Test
    @DisplayName("SKU 没维护预警线（low_qty 为 0 或空）：不报警，避免噪音")
    void skuWithoutThresholdIsIgnored() throws Exception {
        when(inventoryService.list(any(Wrapper.class))).thenReturn(List.of(inventory(10L, 0, "未维护阈值的商品")));
        when(goodsSkuService.listByIds(any())).thenReturn(List.of(sku("10", 0), sku("11", null)));

        Method m = MessageScheduler.class.getDeclaredMethod("checkStockLow");
        m.setAccessible(true);
        m.invoke(scheduler);

        verify(messageService, never()).save(any(SysMessage.class));
    }

    @Test
    @DisplayName("发货超时：超过 4 小时统计单量，未过 24 小时为 medium")
    void orderTimeoutOver4hIsMedium() throws Exception {
        // 第一次 count 查 4 小时线，第二次查 24 小时线
        when(orderService.count(any(Wrapper.class))).thenReturn(6L, 0L);

        SysMessage msg = invoke("checkOrderTimeout");

        assertEquals("order_timeout", msg.getType());
        assertEquals("medium", msg.getLevel());
        assertTrue(msg.getTitle().contains("6"), msg.getTitle());
        assertTrue(msg.getContent().contains("超过 4 小时"), msg.getContent());
    }

    @Test
    @DisplayName("发货超时：有单超过 24 小时则升级为 high")
    void orderTimeoutOver24hIsHigh() throws Exception {
        when(orderService.count(any(Wrapper.class))).thenReturn(6L, 2L);

        SysMessage msg = invoke("checkOrderTimeout");

        assertEquals("high", msg.getLevel());
        assertTrue(msg.getContent().contains("其中 2 单超过 24 小时"), msg.getContent());
    }

    @Test
    @DisplayName("发货超时：没有超时单时不写消息")
    void noTimeoutWritesNothing() throws Exception {
        when(orderService.count(any(Wrapper.class))).thenReturn(0L, 0L);

        Method m = MessageScheduler.class.getDeclaredMethod("checkOrderTimeout");
        m.setAccessible(true);
        m.invoke(scheduler);

        verify(messageService, never()).save(any(SysMessage.class));
    }

    @Test
    @DisplayName("当天已发过同类预警：直接跳过，不再查库存")
    void sameDayAlertIsSkippedBeforeQuery() throws Exception {
        when(messageService.existsByDedupKey(anyString())).thenReturn(true);

        Method m = MessageScheduler.class.getDeclaredMethod("checkStockLow");
        m.setAccessible(true);
        m.invoke(scheduler);

        verify(messageService, never()).save(any(SysMessage.class));
        verify(inventoryService, never()).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("去重键按「类型:日期」生成，插入被唯一索引挡回时不再广播 SSE")
    void duplicateInsertDoesNotBroadcast() throws Exception {
        when(inventoryService.list(any(Wrapper.class))).thenReturn(List.of(inventory(10L, 0, "金项链")));
        when(goodsSkuService.listByIds(any())).thenReturn(List.of(sku("10", 5)));
        // 不模拟 id 回填：save 内部吞掉 DuplicateKeyException，实体拿不到 id
        doNothing().when(messageService).save(any(SysMessage.class));

        Method m = MessageScheduler.class.getDeclaredMethod("checkStockLow");
        m.setAccessible(true);
        m.invoke(scheduler);

        ArgumentCaptor<SysMessage> captor = ArgumentCaptor.forClass(SysMessage.class);
        verify(messageService).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertTrue(captor.getValue().getDedupKey().startsWith("stock_low:"));
        verify(sseService, never()).broadcastMessage(anyString(), any());
    }
}
