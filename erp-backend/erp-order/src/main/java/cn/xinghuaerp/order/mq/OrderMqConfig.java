package cn.xinghuaerp.order.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单事件 MQ 拓扑。
 *
 * <p>主队列绑定死信交换机：消费失败 nack 且不重回队列时消息进入
 * {@code erp.order.created.dlq}，由人工排查后重新投递，不在消费端无限重试。</p>
 *
 * <p>仅当 {@code erp.mq.enabled=true} 时声明；关闭时本配置不生效，
 * 本地无 RabbitMQ 也可正常运行。</p>
 */
@Configuration
@ConditionalOnProperty(name = "erp.mq.enabled", havingValue = "true")
public class OrderMqConfig {

    public static final String ORDER_EXCHANGE = "erp.order";
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String ORDER_CREATED_QUEUE = "erp.order.created";
    public static final String DEAD_LETTER_EXCHANGE = "erp.order.dlx";
    public static final String DEAD_LETTER_QUEUE = "erp.order.created.dlq";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /** 主队列：异常消息经 DLX 转入死信队列，不在队列内反复重试。 */
    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead")
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(orderExchange).with(ORDER_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("dead");
    }
}
