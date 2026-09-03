package cn.ekko.trigger.listener;

import cn.ekko.domain.order.service.IOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RefundSuccessTopicListenerTest {

    private IOrderService orderService;
    private RefundSuccessTopicListener listener;

    @BeforeEach
    void setUp() {
        orderService = mock(IOrderService.class);
        listener = new RefundSuccessTopicListener();
        ReflectionTestUtils.setField(listener, "orderService", orderService);
    }

    @Test
    void unpaidUnlockShouldOnlyConfirmLocalClose() throws Exception {
        listener.listener(message("unpaid_unlock"));

        verify(orderService).confirmUnpaidRefundOrderClosed("user001", "mall-order-1");
        verify(orderService, never()).refundPayOrder("user001", "mall-order-1");
    }

    @Test
    void paidUnformedShouldCallAlipayRefundService() throws Exception {
        listener.listener(message("paid_unformed"));

        verify(orderService).refundPayOrder("user001", "mall-order-1");
    }

    @Test
    void paidFormedShouldCallAlipayRefundService() throws Exception {
        listener.listener(message("paid_formed"));

        verify(orderService).refundPayOrder("user001", "mall-order-1");
    }

    @Test
    void consumerFailureShouldBeRethrownForRabbitRetry() throws Exception {
        doThrow(new IllegalStateException("alipay failed"))
                .when(orderService).refundPayOrder("user001", "mall-order-1");

        assertThrows(IllegalStateException.class, () -> listener.listener(message("paid_unformed")));
    }

    @Test
    void malformedMessageShouldBeRethrownForRabbitRetry() {
        assertThrows(IllegalStateException.class, () -> listener.listener("{\"type\":\"paid_unformed\"}"));
    }

    private String message(String type) {
        return "{\"type\":\"" + type + "\",\"userId\":\"user001\",\"teamId\":\"12345678\"," +
                "\"orderId\":\"group-order-1\",\"outTradeNo\":\"mall-order-1\",\"activityId\":100123}";
    }
}
