package cn.ekko.infrastructure.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class EventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private EventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new EventPublisher();
        ReflectionTestUtils.setField(publisher, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(publisher, "exchangeName", "exchange-1");
        ReflectionTestUtils.setField(publisher, "confirmTimeoutMillis", 1000L);
    }

    @Test
    void shouldReturnOnlyAfterBrokerAck() {
        completeConfirm(true, null);

        publisher.publish("topic.team_refund", "{}");
    }

    @Test
    void shouldFailWhenBrokerNack() {
        completeConfirm(false, "broker rejected");

        assertThrows(IllegalStateException.class,
                () -> publisher.publish("topic.team_refund", "{}"));
    }

    private void completeConfirm(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq("exchange-1"),
                eq("topic.team_refund"),
                eq("{}"),
                any(MessagePostProcessor.class),
                any(CorrelationData.class));
    }
}
