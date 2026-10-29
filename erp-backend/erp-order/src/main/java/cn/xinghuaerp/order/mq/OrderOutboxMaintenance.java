package cn.xinghuaerp.order.mq;

import cn.xinghuaerp.order.facade.OrderNotificationOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="erp.mq.enabled",havingValue="true")
public class OrderOutboxMaintenance {
    private final OrderNotificationOperations operations;
    @Scheduled(cron="${erp.mq.cleanup-cron:0 0 3 * * *}")
    public void cleanup() {operations.pruneSent();}
}
