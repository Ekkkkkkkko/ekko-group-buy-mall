package cn.ekko.trigger.job;

import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.service.IOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundCompensationJobTest {

    private IOrderService orderService;
    private RefundCompensationJob job;

    @BeforeEach
    void setUp() {
        orderService = mock(IOrderService.class);
        job = new RefundCompensationJob();
        ReflectionTestUtils.setField(job, "orderService", orderService);
    }

    @Test
    void shouldContinueCompensatingOtherOrdersAfterOneFailure() throws Exception {
        PayOrderEntity first = PayOrderEntity.builder().userId("u1").orderId("o1").build();
        PayOrderEntity second = PayOrderEntity.builder().userId("u2").orderId("o2").build();
        when(orderService.queryTimeoutWaitRefundOrders()).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("unknown"))
                .when(orderService).refundPayOrder("u1", "o1");

        job.exec();

        verify(orderService).refundPayOrder("u1", "o1");
        verify(orderService).refundPayOrder("u2", "o2");
    }
}
