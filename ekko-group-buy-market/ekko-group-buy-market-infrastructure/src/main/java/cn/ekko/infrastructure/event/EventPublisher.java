package cn.ekko.infrastructure.event;

import cn.ekko.types.event.BaseEvent;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author Ekko
 * @description 消息发送
 */
@Slf4j
@Component
public class EventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.config.producer.exchange}")
    private String exchangeName;

    @Value("${spring.rabbitmq.config.producer.confirm-timeout-ms:5000}")
    private long confirmTimeoutMillis;

    public void publish(String routingKey, String message) {
        try {
            CorrelationData correlationData = new CorrelationData();
            rabbitTemplate.convertAndSend(exchangeName, routingKey, message, m -> {
                // 持久化消息配置
                m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return m;
            }, correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ Broker NACK: " + confirm.getReason());
            }

            ReturnedMessage returnedMessage = correlationData.getReturned();
            if (null != returnedMessage) {
                throw new IllegalStateException(
                        "RabbitMQ message was returned, replyCode=" + returnedMessage.getReplyCode()
                                + ", replyText=" + returnedMessage.getReplyText()
                );
            }
        } catch (Exception e) {
            log.error("发送MQ消息失败 exchange:{} routingKey:{} message:{}", exchangeName, routingKey, message, e);
            throw new IllegalStateException("RabbitMQ消息未被可靠确认", e);
        }
    }

}
