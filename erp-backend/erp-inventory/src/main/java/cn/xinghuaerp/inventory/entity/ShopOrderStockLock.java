package cn.xinghuaerp.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 销售订单库存锁定记录。
 *
 * <p>与 {@link GoodsInventoryLock} 的区别：那张表按「批次」锁，用于调拨申请；下单时还不知道
 * 出哪个批次（批次是出库时才选的），只能按「库存行」锁，所以单独一张表，不混用。</p>
 *
 * <p>锁定动作本身落在 {@code o_goods_inventory.locked_quantity}（原子条件更新），
 * 这张表只负责记「哪个订单占了哪条库存多少」，取消订单或超时时据此精确回补。</p>
 */
@Data
@TableName("o_shop_order_stock_lock")
public class ShopOrderStockLock {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 销售订单ID（shop_order.id） */
    private Long orderId;

    /** 订单明细ID（shop_order_item.id） */
    private Long orderItemId;

    /** 库存行ID（o_goods_inventory.id） */
    private Long inventoryId;

    /** 商品库SKU ID */
    private Long skuId;

    /** 锁定数量 */
    private Integer lockQuantity;

    /** 状态：1-锁定中，2-已核销（出库完成），3-已释放（取消/超时） */
    private Integer status;

    /** 锁定过期时间，到点由定时任务释放，防止异常订单永久占用库存 */
    private LocalDateTime expireTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
