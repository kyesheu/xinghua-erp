package cn.xinghuaerp.order.mq;

import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.service.ISysMessageService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 订单事件消费者：收到"订单转入 OMS"事件后写入站内消息，
 * 复用既有通知调度（SSE / Webhook）触达用户。
 *
 * <p>可靠性约定：手动 ACK；同一订单的重复投递按标题幂等去重；
 * 处理异常 nack 且不重回队列，消息落入死信队列等待人工处理，
 * 不在消费端无限重试阻塞队列。</p>
 *
 * <p>{@code autoStartup} 绑定 {@code erp.mq.enabled}：关闭时监听容器不启动，
 * 不建立 MQ 连接。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    public static final String MESSAGE_TYPE = "ORDER_CREATED";

    private final ISysMessageService sysMessageService;

    @RabbitListener(queues = OrderMqConfig.ORDER_CREATED_QUEUE, autoStartup = "${erp.mq.enabled:false}")
    public void onMessage(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        OrderCreatedEvent event;
        try {
            event = JsonUtils.readValue(new String(message.getBody(), StandardCharsets.UTF_8),
                    OrderCreatedEvent.class);
        } catch (Exception e) {
            log.error("订单事件报文解析失败，转入死信队列：{}", e.getMessage());
            channel.basicNack(tag, false, false);
            return;
        }
        if (event == null || event.orderId() == null || event.orderId() <= 0) {
            log.warn("订单事件缺少有效 orderId，转入死信队列：{}", event);
            channel.basicNack(tag, false, false);
            return;
        }
        try {
            String title = "新订单转入-" + event.orderId();
            if (sysMessageService.existsByTitle(title)) {
                log.info("订单事件重复投递，按标题幂等跳过：orderId={}", event.orderId());
            } else {
                SysMessage msg = new SysMessage();
                msg.setType(MESSAGE_TYPE);
                msg.setLevel("info");
                msg.setTitle(title);
                msg.setContent("平台订单(shopOrderId=" + event.shopOrderId() + ")已转入 OMS(orderId="
                        + event.orderId() + ")，事件ID=" + event.messageId() + "，时间=" + event.occurredAt());
                msg.setSource("mq");
                msg.setIsRead(0);
                msg.setNeedNotify(1);
                msg.setNotifyStatus(0);
                sysMessageService.save(msg);
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("订单事件处理失败，转入死信队列：orderId={}, 原因={}", event.orderId(), e.getMessage(), e);
            channel.basicNack(tag, false, false);
        }
    }
}
