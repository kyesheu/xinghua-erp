package cn.xinghuaerp.order.mq;

import cn.xinghuaerp.sse.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 只在订单提交后通知页面，通知通道故障不改变已提交的业务结果。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCommittedNotifier {
    private final SseService sse;

    @TransactionalEventListener
    public void onCommitted(OrderCommittedNotification event) {
        try {
            sse.broadcastNewOrderMessage(event.shopType(), event.orderNumber());
        } catch (Exception error) {
            log.warn("订单已提交，页面通知失败：orderNumber={}", event.orderNumber(), error);
        }
    }
}
