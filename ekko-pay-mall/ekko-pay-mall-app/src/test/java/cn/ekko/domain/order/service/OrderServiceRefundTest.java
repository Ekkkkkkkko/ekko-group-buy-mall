package cn.ekko.domain.order.service;

import cn.ekko.domain.order.adapter.port.IGroupBuyMarketPort;
import cn.ekko.domain.order.adapter.port.IProductPort;
import cn.ekko.domain.order.adapter.repository.IOrderRepository;
import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import cn.ekko.types.exception.AppException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceRefundTest {

    private IOrderRepository repository;
    private IGroupBuyMarketPort groupBuyMarketPort;
    private AlipayClient alipayClient;
    private OrderService service;

    @BeforeEach
    void setUp() {
        repository = mock(IOrderRepository.class);
        groupBuyMarketPort = mock(IGroupBuyMarketPort.class);
        alipayClient = mock(AlipayClient.class);
        service = new OrderService(repository, mock(IProductPort.class), groupBuyMarketPort);
        ReflectionTestUtils.setField(service, "alipayClient", alipayClient);
    }

    @Test
    void shouldCloseUnpaidMarketOrderAfterGroupBuyRefund() {
        PayOrderEntity order = order(OrderStatusVO.PAY_WAIT, MarketTypeVO.GROUP_BUY_MARKET);
        when(repository.queryOrderByUserIdAndOrderId("1001", "order-1")).thenReturn(order);
        when(repository.refundOrder("1001", "order-1", OrderStatusVO.PAY_WAIT)).thenReturn(1);

        PayOrderEntity result = service.refundOrder("1001", "order-1");

        assertEquals(OrderStatusVO.CLOSE, result.getOrderStatus());
        InOrder inOrder = inOrder(groupBuyMarketPort, repository);
        inOrder.verify(groupBuyMarketPort).refundMarketPayOrder("1001", "order-1");
        inOrder.verify(repository).refundOrder("1001", "order-1", OrderStatusVO.PAY_WAIT);
        verify(repository, never()).refundMarketOrder("1001", "order-1", OrderStatusVO.PAY_WAIT);
    }

    @Test
    void shouldMovePaidOrderToWaitRefund() {
        PayOrderEntity order = order(OrderStatusVO.PAY_SUCCESS, MarketTypeVO.GROUP_BUY_MARKET);
        when(repository.queryOrderByUserIdAndOrderId("1001", "order-1")).thenReturn(order);
        when(repository.refundMarketOrder("1001", "order-1", OrderStatusVO.PAY_SUCCESS)).thenReturn(1);

        PayOrderEntity result = service.refundOrder("1001", "order-1");

        assertEquals(OrderStatusVO.WAIT_REFUND, result.getOrderStatus());
        verify(repository).refundMarketOrder("1001", "order-1", OrderStatusVO.PAY_SUCCESS);
        verify(repository, never()).refundOrder("1001", "order-1", OrderStatusVO.PAY_SUCCESS);
    }

    @Test
    void shouldKeepMallStatusWhenGroupBuyRefundFails() {
        PayOrderEntity order = order(OrderStatusVO.PAY_WAIT, MarketTypeVO.GROUP_BUY_MARKET);
        when(repository.queryOrderByUserIdAndOrderId("1001", "order-1")).thenReturn(order);
        doThrow(new AppException("1001", "remote failed"))
                .when(groupBuyMarketPort).refundMarketPayOrder("1001", "order-1");

        assertThrows(AppException.class, () -> service.refundOrder("1001", "order-1"));

        verify(repository, never()).refundOrder("1001", "order-1", OrderStatusVO.PAY_WAIT);
        verify(repository, never()).refundMarketOrder("1001", "order-1", OrderStatusVO.PAY_WAIT);
    }

    @Test
    void shouldReturnIdempotentSuccessForAlreadyClosedOrder() {
        PayOrderEntity closed = order(OrderStatusVO.CLOSE, MarketTypeVO.GROUP_BUY_MARKET);
        when(repository.queryOrderByUserIdAndOrderId("1001", "order-1")).thenReturn(closed);

        assertEquals(OrderStatusVO.CLOSE, service.refundOrder("1001", "order-1").getOrderStatus());

        verify(groupBuyMarketPort, never()).refundMarketPayOrder("1001", "order-1");
    }

    @Test
    void shouldTreatConcurrentCloseAsIdempotentSuccess() {
        PayOrderEntity payWait = order(OrderStatusVO.PAY_WAIT, MarketTypeVO.NO_MARKET);
        PayOrderEntity closed = order(OrderStatusVO.CLOSE, MarketTypeVO.NO_MARKET);
        when(repository.queryOrderByUserIdAndOrderId("1001", "order-1"))
                .thenReturn(payWait, closed);
        when(repository.refundOrder("1001", "order-1", OrderStatusVO.PAY_WAIT)).thenReturn(0);

        assertEquals(OrderStatusVO.CLOSE, service.refundOrder("1001", "order-1").getOrderStatus());
    }

    @Test
    void shouldRejectMissingOrForeignOrder() {
        when(repository.queryOrderByUserIdAndOrderId("1002", "order-1")).thenReturn(null);

        assertThrows(AppException.class, () -> service.refundOrder("1002", "order-1"));

        verify(groupBuyMarketPort, never()).refundMarketPayOrder("1002", "order-1");
    }

    @Test
    void shouldRefundPaidOrderUsingLocalPayAmountAndClose() throws Exception {
        PayOrderEntity order = order(OrderStatusVO.WAIT_REFUND, MarketTypeVO.GROUP_BUY_MARKET);
        order.setPayAmount(new BigDecimal("12.34"));
        when(repository.queryOrderByUserIdAndOrderId("1001", "order-1")).thenReturn(order);

        AlipayTradeFastpayRefundQueryResponse queryResponse = new AlipayTradeFastpayRefundQueryResponse();
        queryResponse.setCode("10000");
        when(alipayClient.execute(any(AlipayTradeFastpayRefundQueryRequest.class))).thenReturn(queryResponse);

        AlipayTradeRefundResponse refundResponse = new AlipayTradeRefundResponse();
        refundResponse.setCode("10000");
        refundResponse.setFundChange("Y");
        when(alipayClient.execute(any(AlipayTradeRefundRequest.class))).thenReturn(refundResponse);
        when(repository.closeRefundOrder("1001", "order-1")).thenReturn(1);

        service.refundPayOrder("1001", "order-1");

        ArgumentCaptor<AlipayTradeRefundRequest> requestCaptor = ArgumentCaptor.forClass(AlipayTradeRefundRequest.class);
        verify(alipayClient).execute(requestCaptor.capture());
        AlipayTradeRefundModel model = (AlipayTradeRefundModel) requestCaptor.getValue().getBizModel();
        assertEquals("order-1", model.getOutTradeNo());
        assertEquals("REFUND_order-1", model.getOutRequestNo());
        assertEquals("12.34", model.getRefundAmount());
        verify(repository).closeRefundOrder("1001", "order-1");
    }

    @Test
    void shouldCloseFromSuccessfulRefundQueryWithoutCallingRefundAgain() throws Exception {
        PayOrderEntity order = order(OrderStatusVO.WAIT_REFUND, MarketTypeVO.GROUP_BUY_MARKET);
        order.setPayAmount(new BigDecimal("12.34"));
        when(repository.queryOrderByUserIdAndOrderId("1001", "order-1")).thenReturn(order);

        AlipayTradeFastpayRefundQueryResponse queryResponse = new AlipayTradeFastpayRefundQueryResponse();
        queryResponse.setCode("10000");
        queryResponse.setRefundStatus("REFUND_SUCCESS");
        queryResponse.setRefundAmount("12.34");
        when(alipayClient.execute(any(AlipayTradeFastpayRefundQueryRequest.class))).thenReturn(queryResponse);
        when(repository.closeRefundOrder("1001", "order-1")).thenReturn(1);

        service.refundPayOrder("1001", "order-1");

        verify(alipayClient, never()).execute(any(AlipayTradeRefundRequest.class));
        verify(repository).closeRefundOrder("1001", "order-1");
    }

    @Test
    void shouldKeepWaitRefundWhenAlipayRefundFails() throws Exception {
        PayOrderEntity order = order(OrderStatusVO.WAIT_REFUND, MarketTypeVO.GROUP_BUY_MARKET);
        order.setPayAmount(new BigDecimal("12.34"));
        when(repository.queryOrderByUserIdAndOrderId("1001", "order-1")).thenReturn(order);

        AlipayTradeFastpayRefundQueryResponse queryResponse = new AlipayTradeFastpayRefundQueryResponse();
        queryResponse.setCode("10000");
        when(alipayClient.execute(any(AlipayTradeFastpayRefundQueryRequest.class))).thenReturn(queryResponse);

        AlipayTradeRefundResponse refundResponse = new AlipayTradeRefundResponse();
        refundResponse.setCode("40004");
        refundResponse.setSubCode("ACQ.SYSTEM_ERROR");
        when(alipayClient.execute(any(AlipayTradeRefundRequest.class))).thenReturn(refundResponse);

        assertThrows(AppException.class, () -> service.refundPayOrder("1001", "order-1"));

        verify(repository, never()).closeRefundOrder("1001", "order-1");
        assertEquals(OrderStatusVO.WAIT_REFUND, order.getOrderStatus());
    }

    @Test
    void shouldQueryAgainWhenDuplicateRefundResponseHasNoFundChange() throws Exception {
        PayOrderEntity order = order(OrderStatusVO.WAIT_REFUND, MarketTypeVO.GROUP_BUY_MARKET);
        order.setPayAmount(new BigDecimal("12.34"));
        when(repository.queryOrderByUserIdAndOrderId("1001", "order-1")).thenReturn(order);

        AlipayTradeFastpayRefundQueryResponse beforeRefund = new AlipayTradeFastpayRefundQueryResponse();
        beforeRefund.setCode("10000");
        AlipayTradeFastpayRefundQueryResponse afterRefund = new AlipayTradeFastpayRefundQueryResponse();
        afterRefund.setCode("10000");
        afterRefund.setRefundStatus("REFUND_SUCCESS");
        afterRefund.setRefundAmount("12.34");
        when(alipayClient.execute(any(AlipayTradeFastpayRefundQueryRequest.class)))
                .thenReturn(beforeRefund, afterRefund);

        AlipayTradeRefundResponse refundResponse = new AlipayTradeRefundResponse();
        refundResponse.setCode("10000");
        refundResponse.setFundChange("N");
        when(alipayClient.execute(any(AlipayTradeRefundRequest.class))).thenReturn(refundResponse);
        when(repository.closeRefundOrder("1001", "order-1")).thenReturn(1);

        service.refundPayOrder("1001", "order-1");

        verify(alipayClient, org.mockito.Mockito.times(2))
                .execute(any(AlipayTradeFastpayRefundQueryRequest.class));
        verify(repository).closeRefundOrder("1001", "order-1");
    }

    @Test
    void shouldTreatClosedRefundOrderAsIdempotentWithoutAlipayCall() throws Exception {
        PayOrderEntity order = order(OrderStatusVO.CLOSE, MarketTypeVO.GROUP_BUY_MARKET);
        when(repository.queryOrderByUserIdAndOrderId("1001", "order-1")).thenReturn(order);

        service.refundPayOrder("1001", "order-1");

        verify(alipayClient, never()).execute(any(AlipayTradeFastpayRefundQueryRequest.class));
        verify(alipayClient, never()).execute(any(AlipayTradeRefundRequest.class));
    }

    private PayOrderEntity order(OrderStatusVO status, MarketTypeVO marketType) {
        return PayOrderEntity.builder()
                .userId("1001")
                .orderId("order-1")
                .orderStatus(status)
                .marketType(marketType)
                .build();
    }
}
