package cn.ekko.infrastructure.adapter.repository;

import cn.ekko.domain.trade.model.aggregate.GroupBuyRefundAggregate;
import cn.ekko.domain.trade.model.entity.NotifyTaskEntity;
import cn.ekko.domain.trade.model.entity.TradeRefundOrderEntity;
import cn.ekko.infrastructure.dao.IGroupBuyOrderDao;
import cn.ekko.infrastructure.dao.IGroupBuyOrderListDao;
import cn.ekko.infrastructure.dao.INotifyTaskDao;
import cn.ekko.infrastructure.dao.po.NotifyTask;
import cn.ekko.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeRepositoryRefundTest {

    private TradeRepository repository;
    private IGroupBuyOrderListDao orderListDao;
    private IGroupBuyOrderDao orderDao;
    private INotifyTaskDao notifyTaskDao;

    @BeforeEach
    void setUp() {
        repository = new TradeRepository();
        orderListDao = mock(IGroupBuyOrderListDao.class);
        orderDao = mock(IGroupBuyOrderDao.class);
        notifyTaskDao = mock(INotifyTaskDao.class);
        ReflectionTestUtils.setField(repository, "groupBuyOrderListDao", orderListDao);
        ReflectionTestUtils.setField(repository, "groupBuyOrderDao", orderDao);
        ReflectionTestUtils.setField(repository, "notifyTaskDao", notifyTaskDao);
        ReflectionTestUtils.setField(repository, "topic_team_refund", "team_refund");
    }

    @Test
    void shouldUpdateUnpaidDetailAndTeamBeforeCreatingStableTask() {
        when(orderListDao.unpaid2Refund(any())).thenReturn(1);
        when(orderDao.unpaid2Refund(any())).thenReturn(1);

        NotifyTaskEntity result = repository.unpaid2Refund(
                GroupBuyRefundAggregate.buildUnpaid2RefundAggregate(order(), -1));

        assertNotNull(result);
        assertEquals("team-1_trade_unpaid2refund_market-order-1", result.getUuid());
        ArgumentCaptor<NotifyTask> taskCaptor = forClass(NotifyTask.class);
        verify(notifyTaskDao).insert(taskCaptor.capture());
        assertEquals(result.getUuid(), taskCaptor.getValue().getUuid());
    }

    @Test
    void shouldRollbackFlowBeforeTeamAndTaskWhenDetailStateMismatch() {
        when(orderListDao.paid2Refund(any())).thenReturn(0);

        assertThrows(AppException.class, () -> repository.paid2Refund(
                GroupBuyRefundAggregate.buildPaid2RefundAggregate(order(), -1, -1)));

        verify(orderDao, never()).paid2Refund(any());
        verify(notifyTaskDao, never()).insert(any());
    }

    @Test
    void shouldUseSingleAtomicTeamUpdateForPaidFormedRefund() {
        when(orderListDao.paidTeam2Refund(any())).thenReturn(1);
        when(orderDao.paidTeam2Refund(any())).thenReturn(1);

        NotifyTaskEntity result = repository.paidTeam2Refund(
                GroupBuyRefundAggregate.buildPaidTeam2RefundAggregate(order(), -1, -1, null));

        assertEquals("team-1_trade_paid_team2refund_market-order-1", result.getUuid());
        verify(orderDao).paidTeam2Refund(any());
        verify(notifyTaskDao).insert(any());
    }

    private TradeRefundOrderEntity order() {
        return TradeRefundOrderEntity.builder()
                .userId("1001")
                .teamId("team-1")
                .activityId(10001L)
                .orderId("market-order-1")
                .outTradeNo("mall-order-1")
                .build();
    }
}
