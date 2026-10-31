package cn.xinghuaerp.inventory.service.impl;

import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.inventory.entity.ShopOrderStockLock;
import cn.xinghuaerp.inventory.mapper.ShopOrderStockLockMapper;
import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 下单锁库的行为用例：校验、按行占用、跨行拆分、并发抢占、释放回补。
 *
 * <p>回归背景：{@code createOrder} 里的 {@code if (order.getIsLock()==1) { }} 是个空块，
 * 前端传了「锁库」后端收下就丢，{@code lockStock} 零调用方，并发下单会超卖。</p>
 */
class ShopOrderStockLockTest {

    private OGoodsInventoryService inventoryService;
    private ShopOrderStockLockMapper mapper;
    private ShopOrderStockLockServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        inventoryService = mock(OGoodsInventoryService.class);
        mapper = mock(ShopOrderStockLockMapper.class);
        when(mapper.insert(any(ShopOrderStockLock.class))).thenReturn(1);
        when(mapper.updateById(any(ShopOrderStockLock.class))).thenReturn(1);
        service = new ShopOrderStockLockServiceImpl(inventoryService);
        // ServiceImpl 的 baseMapper 在父类里（3.5.x 挪到了 CrudRepository），逐层往上找，
        // 单测直接塞 mock，不启 Spring。
        Class<?> type = service.getClass();
        Field field = null;
        while (type != null && field == null) {
            try {
                field = type.getDeclaredField("baseMapper");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (field == null) throw new IllegalStateException("找不到 baseMapper 字段");
        field.setAccessible(true);
        field.set(service, mapper);
    }

    private OGoodsInventory row(long id, long skuId, int available) {
        OGoodsInventory inv = new OGoodsInventory();
        inv.setId(id);
        inv.setSkuId(skuId);
        inv.setAvailableQuantity(available);
        inv.setGoodsName("金项链");
        inv.setSkuName("20cm");
        return inv;
    }

    private Map<Long, Integer> need(long skuId, int qty) {
        Map<Long, Integer> map = new LinkedHashMap<>();
        map.put(skuId, qty);
        return map;
    }

    @Test
    @DisplayName("可用量不足：返回带可用量和需求量的提示，不写库")
    void checkAvailableReportsShortage() {
        when(inventoryService.list(any(Wrapper.class))).thenReturn(List.of(row(1L, 10L, 3)));

        String shortage = service.checkAvailable(1L, need(10L, 5));

        assertTrue(shortage != null && shortage.contains("可用 3 件") && shortage.contains("需要 5 件"),
                shortage);
        verify(mapper, never()).insert(any(ShopOrderStockLock.class));
    }

    @Test
    @DisplayName("可用量够：返回 null")
    void checkAvailablePasses() {
        when(inventoryService.list(any(Wrapper.class))).thenReturn(List.of(row(1L, 10L, 8)));

        assertEquals(null, service.checkAvailable(1L, need(10L, 5)));
    }

    @Test
    @DisplayName("锁库：占用库存行并落一条锁定记录，数量等于订单需求")
    void lockWritesRecordAndCallsAdjust() {
        when(inventoryService.list(any(Wrapper.class))).thenReturn(List.of(row(7L, 10L, 8)));
        when(inventoryService.lockStock(7L, 5)).thenReturn(true);

        service.lockForOrder(100L, 1L, need(10L, 5), LocalDateTime.now().plusMinutes(30));

        verify(inventoryService).lockStock(7L, 5);
        ArgumentCaptor<ShopOrderStockLock> captor = ArgumentCaptor.forClass(ShopOrderStockLock.class);
        verify(mapper).insert(captor.capture());
        ShopOrderStockLock lock = captor.getValue();
        assertEquals(100L, lock.getOrderId());
        assertEquals(7L, lock.getInventoryId());
        assertEquals(10L, lock.getSkuId());
        assertEquals(5, lock.getLockQuantity());
        assertEquals(1, lock.getStatus());
    }

    @Test
    @DisplayName("单行不够：按可用量从大到小拆到多行，落多条记录")
    void lockSplitsAcrossRows() {
        when(inventoryService.list(any(Wrapper.class)))
                .thenReturn(List.of(row(2L, 10L, 5), row(1L, 10L, 3)));
        when(inventoryService.lockStock(2L, 5)).thenReturn(true);
        when(inventoryService.lockStock(1L, 1)).thenReturn(true);

        service.lockForOrder(100L, 1L, need(10L, 6), LocalDateTime.now().plusMinutes(30));

        verify(inventoryService).lockStock(2L, 5);
        verify(inventoryService).lockStock(1L, 1);
        verify(mapper, times(2)).insert(any(ShopOrderStockLock.class));
    }

    @Test
    @DisplayName("锁的瞬间被并发订单抢走：抛异常让事务回滚，不留下半截锁定")
    void lockFailsWhenRowStolenConcurrently() {
        when(inventoryService.list(any(Wrapper.class))).thenReturn(List.of(row(7L, 10L, 5)));
        when(inventoryService.lockStock(anyLong(), anyInt())).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.lockForOrder(100L, 1L, need(10L, 5), LocalDateTime.now().plusMinutes(30)));
        assertTrue(ex.getMessage().contains("请重试"), ex.getMessage());
        verify(mapper, never()).insert(any(ShopOrderStockLock.class));
    }

    @Test
    @DisplayName("取消订单释放：回补负数量并置为已释放，幂等靠状态过滤")
    void releaseRestoresAvailableAndMarksReleased() {
        ShopOrderStockLock lock = new ShopOrderStockLock();
        lock.setId(9L);
        lock.setOrderId(100L);
        lock.setInventoryId(7L);
        lock.setLockQuantity(4);
        lock.setStatus(1);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(lock));
        when(inventoryService.lockStock(eq(7L), eq(-4))).thenReturn(true);

        int released = service.releaseForOrder(100L);

        assertEquals(1, released);
        verify(inventoryService).lockStock(7L, -4);
        ArgumentCaptor<ShopOrderStockLock> captor = ArgumentCaptor.forClass(ShopOrderStockLock.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(3, captor.getValue().getStatus());
    }
}
