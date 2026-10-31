package cn.xinghuaerp.erp.task;

import cn.xinghuaerp.inventory.facade.InventoryFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单库存锁定的兜底释放。
 *
 * <p>正常路径靠「取消订单」释放；这里处理异常路径：下单后流程中断、订单被直接删除、
 * 或系统在释放前重启——锁定记录留在 status=1，可用量会一直被占着，
 * 库存看起来「有货但卖不出去」，很难排查。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.stock.lock-scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class StockLockScheduler {

    private final InventoryFacade inventoryFacade;

    /** 每 5 分钟扫一次。单次最多处理 200 条，避免一次扫太多把事务拖长。 */
    @Scheduled(fixedRate = 300000)
    public void releaseExpired() {
        int released = inventoryFacade.releaseExpiredStockLocks();
        if (released > 0) {
            log.info("释放过期库存锁定 {} 条", released);
        }
    }
}
