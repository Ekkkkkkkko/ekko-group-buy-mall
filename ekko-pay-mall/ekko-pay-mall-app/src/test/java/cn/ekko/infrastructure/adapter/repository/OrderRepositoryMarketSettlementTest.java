package cn.ekko.infrastructure.adapter.repository;

import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import cn.ekko.infrastructure.dao.IOrderDao;
import cn.ekko.infrastructure.dao.po.PayOrder;
import cn.ekko.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderRepositoryMarketSettlementTest {

    private OrderRepository repository;
    private IOrderDao orderDao;

    @BeforeEach
    void setUp() {
        repository = new OrderRepository();
        orderDao = mock(IOrderDao.class);
        ReflectionTestUtils.setField(repository, "orderDao", orderDao);
    }

    @Test
    void shouldBatchUpdateOnlyPaySuccessOrders() {
        PayOrder paySuccessOrder = order("1001", "order-1", "PAY_SUCCESS");
        PayOrder dealDoneOrder = order("1002", "order-2", "DEAL_DONE");
        when(orderDao.queryMarketOrdersByTeamIdAndOrderIds("team-1", List.of("order-1", "order-2")))
                .thenReturn(List.of(paySuccessOrder, dealDoneOrder));
        when(orderDao.changeOrderMarketSettlement("team-1", List.of("order-1"))).thenReturn(1);

        List<PayOrderEntity> result = repository.changeOrderMarketSettlement(
                "team-1", List.of("order-1", "order-2"));

        assertEquals(2, result.size());
        assertEquals(OrderStatusVO.PAY_SUCCESS, result.get(0).getOrderStatus());
        assertEquals(OrderStatusVO.DEAL_DONE, result.get(1).getOrderStatus());
        verify(orderDao).changeOrderMarketSettlement("team-1", List.of("order-1"));
    }

    @Test
    void shouldNotBatchUpdateDuplicateNotify() {
        when(orderDao.queryMarketOrdersByTeamIdAndOrderIds("team-2", List.of("order-3")))
                .thenReturn(List.of(order("1003", "order-3", "MARKET")));

        repository.changeOrderMarketSettlement("team-2", List.of("order-3"));

        verify(orderDao, never()).changeOrderMarketSettlement("team-2", List.of("order-3"));
    }

    @Test
    void shouldRejectUnexpectedBatchUpdateCount() {
        when(orderDao.queryMarketOrdersByTeamIdAndOrderIds("team-3", List.of("order-4")))
                .thenReturn(List.of(order("1004", "order-4", "PAY_SUCCESS")));
        when(orderDao.changeOrderMarketSettlement("team-3", List.of("order-4"))).thenReturn(0);

        assertThrows(AppException.class,
                () -> repository.changeOrderMarketSettlement("team-3", List.of("order-4")));
    }

    private PayOrder order(String userId, String orderId, String status) {
        return PayOrder.builder()
                .userId(userId)
                .orderId(orderId)
                .status(status)
                .marketType(1)
                .teamId("team")
                .build();
    }

}
