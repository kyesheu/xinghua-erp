package cn.xinghuaerp.order.mq;

import cn.xinghuaerp.json.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 订单事件生产者：订单转入 OMS 成功后发布事件，下游通知异步消费。
 *
 * <p>发布失败只记录日志、不向上抛出——订单同步主流程不因通知通道故障而失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.mq.enabled", havingValue = "true")
public class OrderEventPublisher {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCreated(Long shopOrderId, Long orderId) {
        if (shopOrderId == null || shopOrderId <= 0) {
            return;
        }
        try {
            var event = new OrderCreatedEvent(UUID.randomUUID().toString(), shopOrderId, orderId,
                    LocalDateTime.now().format(DT));
            rabbitTemplate.convertAndSend(OrderMqConfig.ORDER_EXCHANGE,
                    OrderMqConfig.ORDER_CREATED_ROUTING_KEY, JsonUtils.write(event));
            log.info("订单事件已发布：shopOrderId={}, orderId={}", shopOrderId, orderId);
        } catch (Exception e) {
            log.warn("订单事件发布失败，不影响同步主流程：shopOrderId={}, orderId={}, 原因={}",
                    shopOrderId, orderId, e.getMessage());
        }
    }
}
