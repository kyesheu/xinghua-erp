package cn.xinghuaerp.order.mq;

/**
 * 订单转入 OMS 事件载荷；通过 RabbitMQ 在订单同步与下游通知之间解耦。
 *
 * @param messageId   消息唯一 ID（UUID），供消费端幂等与链路排查
 * @param shopOrderId 平台原始订单内部主键（o_shop_order.id）
 * @param orderId     转入后的 OMS 订单主键（o_order.id）
 * @param occurredAt  事件产生时间（yyyy-MM-dd HH:mm:ss）
 */
public record OrderCreatedEvent(String messageId, Long shopOrderId, Long orderId, String occurredAt) {
}
