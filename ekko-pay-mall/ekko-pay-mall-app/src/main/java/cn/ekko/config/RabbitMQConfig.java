package cn.ekko.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商城退款消息拓扑。主队列与拼团内部恢复库存队列必须分开。
 */
@Configuration
public class RabbitMQConfig {

    @Bean("teamRefundExchange")
    public TopicExchange teamRefundExchange(
            @Value("${spring.rabbitmq.config.consumer.topic_team_refund.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean("teamRefundDeadLetterExchange")
    public DirectExchange teamRefundDeadLetterExchange(
            @Value("${spring.rabbitmq.config.consumer.topic_team_refund.dead_letter_exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean("teamRefundQueue")
    public Queue teamRefundQueue(
            @Value("${spring.rabbitmq.config.consumer.topic_team_refund.queue}") String queueName,
            @Value("${spring.rabbitmq.config.consumer.topic_team_refund.dead_letter_exchange}") String deadLetterExchange,
            @Value("${spring.rabbitmq.config.consumer.topic_team_refund.dead_letter_routing_key}") String deadLetterRoutingKey) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean("teamRefundDeadLetterQueue")
    public Queue teamRefundDeadLetterQueue(
            @Value("${spring.rabbitmq.config.consumer.topic_team_refund.dead_letter_queue}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding teamRefundBinding(
            @Qualifier("teamRefundQueue") Queue queue,
            @Qualifier("teamRefundExchange") TopicExchange exchange,
            @Value("${spring.rabbitmq.config.consumer.topic_team_refund.routing_key}") String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    @Bean
    public Binding teamRefundDeadLetterBinding(
            @Qualifier("teamRefundDeadLetterQueue") Queue queue,
            @Qualifier("teamRefundDeadLetterExchange") DirectExchange exchange,
            @Value("${spring.rabbitmq.config.consumer.topic_team_refund.dead_letter_routing_key}") String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
