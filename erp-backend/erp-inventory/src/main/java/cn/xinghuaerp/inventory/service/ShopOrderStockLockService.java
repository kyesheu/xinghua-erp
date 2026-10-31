package cn.xinghuaerp.inventory.service;

import cn.xinghuaerp.inventory.entity.ShopOrderStockLock;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 销售订单库存锁定服务。
 *
 * <p>下单即占用库存，防止并发下单把同一件货卖两次；取消订单或锁定超时后回补。
 * 真正的加减落在 {@code o_goods_inventory.locked_quantity} 上，本服务只负责
 * 校验、编排和记录，所有写操作都是条件更新，靠影响行数判断是否成功。</p>
 */
public interface ShopOrderStockLockService extends IService<ShopOrderStockLock> {

    /**
     * 只读校验：这些 SKU 的可用量是否够。
     *
     * <p>先校验再锁，是为了让「库存不足」走正常的业务错误返回，而不是在事务里抛异常
     * （本项目没有全局异常处理器，异常会变成 500）。真正的并发抢占由 {@link #lockForOrder}
     * 里的原子更新兜底。</p>
     *
     * @param merchantId     商户ID，多租户隔离
     * @param skuQuantities  SKU ID → 需要锁定的数量（同一 SKU 多行已由调用方合并）
     * @return {@code null} 表示够；否则是给用户看的提示，例如「库存不足：金项链 还差 2 件」
     */
    String checkAvailable(Long merchantId, Map<Long, Integer> skuQuantities);

    /**
     * 锁定库存并落记录。{@link #checkAvailable} 通过后调用。
     *
     * @throws IllegalStateException 锁定时该库存行已被并发订单抢走（影响行数为 0）
     */
    void lockForOrder(Long orderId, Long merchantId, Map<Long, Integer> skuQuantities,
                      LocalDateTime expireTime);

    /**
     * 释放某订单仍在锁定中的库存，返回释放的记录数。幂等：已释放的不会重复回补。
     */
    int releaseForOrder(Long orderId);

    /**
     * 释放所有已过期且仍在锁定中的记录，返回处理条数。
     * 兜底异常路径（下单后流程中断、订单被直接删除等），避免库存被永久占用。
     */
    int releaseExpired();
}
