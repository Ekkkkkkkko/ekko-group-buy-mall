package cn.ekko.trigger.job;

import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.service.IOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketPaySettlementJobTest {

    private IOrderService orderService;
    private MarketPaySettlementJob job;

    @BeforeEach
    void setUp() {
        orderService = mock(IOrderService.class);
        job = new MarketPaySettlementJob();
        ReflectionTestUtils.setField(job, "orderService", orderService);
    }

    @Test
    void shouldRetryEveryPendingMarketSettlementOrderIndependently() {
        Date firstPayTime = new Date(1000L);
        Date secondPayTime = new Date(2000L);
        PayOrderEntity firstOrder = PayOrderEntity.builder()
                .userId("1001")
                .orderId("order-1")
                .payTime(firstPayTime)
                .build();
        PayOrderEntity secondOrder = PayOrderEntity.builder()
                .userId("1002")
                .orderId("order-2")
                .payTime(secondPayTime)
                .build();
        when(orderService.queryPendingMarketSettlementOrders())
                .thenReturn(List.of(firstOrder, secondOrder));
        when(orderService.changeOrderPaySuccess("order-1", firstPayTime))
                .thenThrow(new RuntimeException("remote timeout"));
        when(orderService.changeOrderPaySuccess("order-2", secondPayTime))
                .thenReturn(true);

        job.exec();

        verify(orderService).changeOrderPaySuccess("order-1", firstPayTime);
        verify(orderService).changeOrderPaySuccess("order-2", secondPayTime);
    }
}
