package cn.ekko.domain.order.service;

import cn.ekko.domain.order.adapter.port.IGroupBuyMarketPort;
import cn.ekko.domain.order.adapter.port.IProductPort;
import cn.ekko.domain.order.adapter.repository.IOrderRepository;
import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import cn.ekko.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceSettlementTest {

    private IOrderRepository repository;
    private IGroupBuyMarketPort groupBuyMarketPort;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        repository = mock(IOrderRepository.class);
        IProductPort productPort = mock(IProductPort.class);
        groupBuyMarketPort = mock(IGroupBuyMarketPort.class);
        orderService = new OrderService(repository, productPort, groupBuyMarketPort);
    }

    @Test
    void shouldPublishNormalOrderOnlyOnFirstPaySuccess() {
        Date payTime = new Date();
        PayOrderEntity order = order("1001", "order-1", MarketTypeVO.NO_MARKET, OrderStatusVO.PAY_WAIT, null);
        when(repository.queryOrderByOrderId("order-1")).thenReturn(order);
        when(repository.changeOrderPaySuccess("order-1", payTime)).thenReturn(1);

        assertTrue(orderService.changeOrderPaySuccess("order-1", payTime));

        verify(repository).publishPaySuccessEvent("1001", "order-1");
        verify(groupBuyMarketPort, never()).settlementMarketPayOrder("1001", "order-1", payTime);
    }

    @Test
    void shouldNotPublishNormalOrderAgainForDuplicateCallback() {
        Date payTime = new Date();
        PayOrderEntity paidOrder = order("1001", "order-2", MarketTypeVO.NO_MARKET, OrderStatusVO.PAY_SUCCESS, payTime);
        when(repository.queryOrderByOrderId("order-2")).thenReturn(paidOrder);
        when(repository.changeOrderPaySuccess("order-2", payTime)).thenReturn(0);

        assertTrue(orderService.changeOrderPaySuccess("order-2", payTime));

        verify(repository, never()).publishPaySuccessEvent("1001", "order-2");
        verify(groupBuyMarketPort, never()).settlementMarketPayOrder("1001", "order-2", payTime);
    }

    @Test
    void shouldAcknowledgeDuplicatePayCallbackWithoutResettlingWaitRefundOrder() {
        Date payTime = new Date();
        PayOrderEntity waitRefundOrder = order(
                "1001", "order-refund", MarketTypeVO.GROUP_BUY_MARKET, OrderStatusVO.WAIT_REFUND, payTime);
        when(repository.queryOrderByOrderId("order-refund")).thenReturn(waitRefundOrder);
        when(repository.changeOrderPaySuccess("order-refund", payTime)).thenReturn(0);

        assertTrue(orderService.changeOrderPaySuccess("order-refund", payTime));

        verify(groupBuyMarketPort, never()).settlementMarketPayOrder("1001", "order-refund", payTime);
        verify(repository, never()).publishPaySuccessEvent("1001", "order-refund");
    }

    @Test
    void shouldSettleGroupBuyOrderAndMarkConfirmation() {
        Date payTime = new Date();
        PayOrderEntity order = order("1002", "order-3", MarketTypeVO.GROUP_BUY_MARKET, OrderStatusVO.PAY_WAIT, null);
        when(repository.queryOrderByOrderId("order-3")).thenReturn(order);
        when(repository.changeOrderPaySuccess("order-3", payTime)).thenReturn(1);

        assertTrue(orderService.changeOrderPaySuccess("order-3", payTime));

        InOrder inOrder = inOrder(repository, groupBuyMarketPort);
        inOrder.verify(repository).changeOrderPaySuccess("order-3", payTime);
        inOrder.verify(groupBuyMarketPort).settlementMarketPayOrder("1002", "order-3", payTime);
        inOrder.verify(repository).markMarketSettlementCompleted("order-3");
        verify(repository, never()).publishPaySuccessEvent("1002", "order-3");
    }

    @Test
    void shouldKeepLocalPayUpdateWhenRemoteSettlementFails() {
        Date payTime = new Date();
        PayOrderEntity order = order("1003", "order-4", MarketTypeVO.GROUP_BUY_MARKET, OrderStatusVO.PAY_WAIT, null);
        when(repository.queryOrderByOrderId("order-4")).thenReturn(order);
        when(repository.changeOrderPaySuccess("order-4", payTime)).thenReturn(1);
        doThrow(new AppException("1001", "remote unavailable"))
                .when(groupBuyMarketPort).settlementMarketPayOrder("1003", "order-4", payTime);

        assertThrows(AppException.class, () -> orderService.changeOrderPaySuccess("order-4", payTime));

        verify(repository).changeOrderPaySuccess("order-4", payTime);
        verify(repository, never()).markMarketSettlementCompleted("order-4");
        verify(repository, never()).publishPaySuccessEvent("1003", "order-4");
    }

    @Test
    void shouldStopWhenLocalOrderDoesNotExist() {
        Date payTime = new Date();
        when(repository.queryOrderByOrderId("missing-order")).thenReturn(null);

        assertFalse(orderService.changeOrderPaySuccess("missing-order", payTime));

        verify(repository, never()).changeOrderPaySuccess("missing-order", payTime);
    }

    @Test
    void shouldPublishFulfillmentOnlyForFirstMarketTransition() {
        PayOrderEntity firstMarketOrder = order(
                "1004", "order-5", MarketTypeVO.GROUP_BUY_MARKET, OrderStatusVO.PAY_SUCCESS, new Date());
        when(repository.changeOrderMarketSettlement("team-1", List.of("order-5")))
                .thenReturn(List.of(firstMarketOrder));

        assertTrue(orderService.groupBuyNotify("team-1", List.of("order-5", "order-5")));

        verify(repository).publishMarketSettlementEvent("1004", "order-5");
    }

    @Test
    void shouldNotPublishFulfillmentForDuplicateGroupBuyNotify() {
        PayOrderEntity completedOrder = order(
                "1005", "order-6", MarketTypeVO.GROUP_BUY_MARKET, OrderStatusVO.DEAL_DONE, new Date());
        when(repository.changeOrderMarketSettlement("team-2", List.of("order-6")))
                .thenReturn(List.of(completedOrder));

        assertTrue(orderService.groupBuyNotify("team-2", List.of("order-6")));

        verify(repository, never()).publishMarketSettlementEvent("1005", "order-6");
    }

    @Test
    void shouldReturnErrorForMissingOrderAndKeepMatchedOrderIdempotent() {
        PayOrderEntity firstMarketOrder = order(
                "1006", "order-7", MarketTypeVO.GROUP_BUY_MARKET, OrderStatusVO.PAY_SUCCESS, new Date());
        when(repository.changeOrderMarketSettlement("team-3", List.of("order-7", "missing-order")))
                .thenReturn(List.of(firstMarketOrder));

        assertFalse(orderService.groupBuyNotify("team-3", List.of("order-7", "missing-order")));

        verify(repository).publishMarketSettlementEvent("1006", "order-7");
    }

    @Test
    void shouldChangeMarketOrderToDealDoneOnlyOnce() {
        when(repository.changeOrderDealDone("order-8")).thenReturn(1, 0);

        assertTrue(orderService.changeOrderDealDone("order-8"));
        assertFalse(orderService.changeOrderDealDone("order-8"));
    }

    private PayOrderEntity order(
            String userId,
            String orderId,
            MarketTypeVO marketType,
            OrderStatusVO orderStatus,
            Date payTime) {
        return PayOrderEntity.builder()
                .userId(userId)
                .orderId(orderId)
                .marketType(marketType)
                .orderStatus(orderStatus)
                .payTime(payTime)
                .build();
    }
}
