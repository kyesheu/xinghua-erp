package cn.xinghuaerp.inventory.service.impl;

import cn.xinghuaerp.inventory.entity.OGoodsInventory;
import cn.xinghuaerp.inventory.entity.ShopOrderStockLock;
import cn.xinghuaerp.inventory.mapper.ShopOrderStockLockMapper;
import cn.xinghuaerp.inventory.service.OGoodsInventoryService;
import cn.xinghuaerp.inventory.service.ShopOrderStockLockService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 销售订单库存锁定实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopOrderStockLockServiceImpl
        extends ServiceImpl<ShopOrderStockLockMapper, ShopOrderStockLock>
        implements ShopOrderStockLockService {

    private static final int STATUS_LOCKED = 1;
    private static final int STATUS_RELEASED = 3;

    private final OGoodsInventoryService inventoryService;

    @Override
    public String checkAvailable(Long merchantId, Map<Long, Integer> skuQuantities) {
        for (Map.Entry<Long, Integer> entry : skuQuantities.entrySet()) {
            int need = entry.getValue() == null ? 0 : entry.getValue();
            if (need <= 0) continue;
            List<OGoodsInventory> rows = candidates(merchantId, entry.getKey());
            int available = rows.stream()
                    .mapToInt(r -> r.getAvailableQuantity() == null ? 0 : r.getAvailableQuantity())
                    .sum();
            if (available < need) {
                return "库存不足：" + displayName(rows, entry.getKey())
                        + " 可用 " + available + " 件，需要 " + need + " 件";
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockForOrder(Long orderId, Long merchantId, Map<Long, Integer> skuQuantities,
                             LocalDateTime expireTime) {
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<Long, Integer> entry : skuQuantities.entrySet()) {
            int need = entry.getValue() == null ? 0 : entry.getValue();
            if (need <= 0) continue;

            Long skuId = entry.getKey();
            for (OGoodsInventory row : candidates(merchantId, skuId)) {
                if (need <= 0) break;
                int available = row.getAvailableQuantity() == null ? 0 : row.getAvailableQuantity();
                int take = Math.min(available, need);
                if (take <= 0) continue;
                // 条件更新：可用量不足或已被并发订单抢走时影响行数为 0，换下一行继续。
                if (!inventoryService.lockStock(row.getId(), take)) continue;

                ShopOrderStockLock lock = new ShopOrderStockLock();
                lock.setOrderId(orderId);
                lock.setInventoryId(row.getId());
                lock.setSkuId(skuId);
                lock.setLockQuantity(take);
                lock.setStatus(STATUS_LOCKED);
                lock.setExpireTime(expireTime);
                lock.setCreatedTime(now);
                lock.setUpdatedTime(now);
                save(lock);

                need -= take;
            }
            // checkAvailable 已按可用量总和校验过，走到这里还差量说明锁的瞬间被并发单抢走了。
            // 抛出后事务回滚，前面已锁的行一并撤销。
            if (need > 0) {
                throw new IllegalStateException("库存已被并发订单占用，请重试（SKU " + skuId
                        + " 还差 " + need + " 件）");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseForOrder(Long orderId) {
        if (orderId == null) return 0;
        return release(list(new LambdaQueryWrapper<ShopOrderStockLock>()
                .eq(ShopOrderStockLock::getOrderId, orderId)
                .eq(ShopOrderStockLock::getStatus, STATUS_LOCKED)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseExpired() {
        return release(list(new LambdaQueryWrapper<ShopOrderStockLock>()
                .eq(ShopOrderStockLock::getStatus, STATUS_LOCKED)
                .lt(ShopOrderStockLock::getExpireTime, LocalDateTime.now())
                .orderByAsc(ShopOrderStockLock::getId)
                .last("LIMIT 200")));
    }

    /**
     * 逐条回补并置为已释放。
     *
     * <p>回补失败（库存行被删、锁定数对不上导致会减成负数）也照样置为已释放：
     * 留在「锁定中」只会让定时任务每次重扫同一条坏记录，而可用量并不会因此多出来——
     * 那种情况是数据已经错了，需要人工盘点，不是重试能修好的。</p>
     */
    private int release(List<ShopOrderStockLock> locks) {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (ShopOrderStockLock lock : locks) {
            int qty = lock.getLockQuantity() == null ? 0 : lock.getLockQuantity();
            if (qty > 0 && !inventoryService.lockStock(lock.getInventoryId(), -qty)) {
                log.warn("释放库存锁定失败，按已释放处理 lockId={} inventoryId={} qty={}",
                        lock.getId(), lock.getInventoryId(), qty);
            }
            ShopOrderStockLock update = new ShopOrderStockLock();
            update.setId(lock.getId());
            update.setStatus(STATUS_RELEASED);
            update.setUpdatedTime(now);
            updateById(update);
            count++;
        }
        return count;
    }

    private List<OGoodsInventory> candidates(Long merchantId, Long skuId) {
        LambdaQueryWrapper<OGoodsInventory> w = new LambdaQueryWrapper<OGoodsInventory>()
                .eq(OGoodsInventory::getSkuId, skuId)
                .eq(OGoodsInventory::getIsDelete, 0)
                .eq(OGoodsInventory::getStockStatus, 1)
                .gt(OGoodsInventory::getAvailableQuantity, 0)
                .eq(merchantId != null && merchantId > 0, OGoodsInventory::getMerchantId, merchantId)
                .orderByDesc(OGoodsInventory::getAvailableQuantity);
        return inventoryService.list(w);
    }

    private String displayName(List<OGoodsInventory> rows, Long skuId) {
        if (rows.isEmpty()) return "SKU " + skuId;
        OGoodsInventory row = rows.get(0);
        String goods = row.getGoodsName() == null ? "" : row.getGoodsName();
        String sku = row.getSkuName() == null ? "" : row.getSkuName();
        if (goods.isEmpty() && sku.isEmpty()) return "SKU " + skuId;
        return "「" + goods + (sku.isEmpty() ? "" : " " + sku) + "」";
    }
}
