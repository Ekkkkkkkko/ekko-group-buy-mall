package cn.ekko.trigger.job;

import cn.ekko.domain.order.service.IOrderService;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoPayNotifyOrderJobTest {

    private IOrderService orderService;
    private AlipayClient alipayClient;
    private NoPayNotifyOrderJob job;

    @BeforeEach
    void setUp() {
        orderService = mock(IOrderService.class);
        alipayClient = mock(AlipayClient.class);
        job = new NoPayNotifyOrderJob();
        ReflectionTestUtils.setField(job, "orderService", orderService);
        ReflectionTestUtils.setField(job, "alipayClient", alipayClient);
    }

    @Test
    void shouldBackOffWhenAlipayTradeDoesNotExist() throws Exception {
        when(orderService.queryNoPayNotifyOrder()).thenReturn(List.of("order-1"));
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.TRADE_NOT_EXIST");
        when(alipayClient.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        job.exec();
        job.exec();

        verify(alipayClient, times(1)).execute(any(AlipayTradeQueryRequest.class));
        verify(orderService, never()).changeOrderPaySuccess(any(), any());
    }

    @Test
    void shouldApplySuccessfulPayment() throws Exception {
        Date payTime = new Date();
        when(orderService.queryNoPayNotifyOrder()).thenReturn(List.of("order-2"));
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("10000");
        response.setTradeStatus("TRADE_SUCCESS");
        response.setSendPayDate(payTime);
        when(alipayClient.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        job.exec();

        verify(orderService).changeOrderPaySuccess("order-2", payTime);
    }

    @Test
    void shouldContinueWithOtherOrdersWhenOneQueryFails() throws Exception {
        Date payTime = new Date();
        when(orderService.queryNoPayNotifyOrder()).thenReturn(List.of("order-3", "order-4"));
        AlipayTradeQueryResponse paidResponse = new AlipayTradeQueryResponse();
        paidResponse.setCode("10000");
        paidResponse.setTradeStatus("TRADE_FINISHED");
        paidResponse.setSendPayDate(payTime);
        when(alipayClient.execute(any(AlipayTradeQueryRequest.class)))
                .thenThrow(new AlipayApiException("sandbox timeout"))
                .thenReturn(paidResponse);

        job.exec();

        verify(alipayClient, times(2)).execute(any(AlipayTradeQueryRequest.class));
        verify(orderService).changeOrderPaySuccess("order-4", payTime);
    }
}
