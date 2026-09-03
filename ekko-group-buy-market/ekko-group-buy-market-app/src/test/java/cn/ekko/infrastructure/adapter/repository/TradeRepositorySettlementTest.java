package cn.ekko.infrastructure.adapter.repository;

import cn.ekko.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.ekko.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.ekko.domain.trade.model.entity.TradePaySuccessEntity;
import cn.ekko.domain.trade.model.entity.UserEntity;
import cn.ekko.domain.trade.model.entity.NotifyTaskEntity;
import cn.ekko.domain.trade.model.valobj.NotifyConfigVO;
import cn.ekko.domain.trade.model.valobj.NotifyTypeEnumVO;
import cn.ekko.infrastructure.dao.IGroupBuyOrderDao;
import cn.ekko.infrastructure.dao.IGroupBuyOrderListDao;
import cn.ekko.infrastructure.dao.INotifyTaskDao;
import cn.ekko.infrastructure.dao.po.GroupBuyOrderList;
import cn.ekko.infrastructure.dao.po.NotifyTask;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeRepositorySettlementTest {

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
    }

    @Test
    void shouldReturnIdempotentSuccessWhenOrderDetailAlreadyCompleted() {
        GroupBuyTeamSettlementAggregate aggregate = aggregate();
        when(orderListDao.updateOrderStatus2COMPLETE(any())).thenReturn(0);
        when(orderListDao.queryGroupBuyOrderRecordByOutTradeNo(any()))
                .thenReturn(GroupBuyOrderList.builder().teamId("team-1").status(1).build());

        assertNull(repository.settlementMarketPayOrder(aggregate));

        verify(orderDao, never()).updateAddCompleteCount("team-1");
        verify(notifyTaskDao, never()).insert(any());
    }

    @Test
    void shouldIncreaseCompleteCountOnlyAfterFirstDetailTransition() {
        GroupBuyTeamSettlementAggregate aggregate = aggregate();
        when(orderListDao.updateOrderStatus2COMPLETE(any())).thenReturn(1);
        when(orderDao.updateAddCompleteCount("team-1")).thenReturn(1);
        when(orderDao.updateOrderStatus2COMPLETE("team-1")).thenReturn(0);

        assertNull(repository.settlementMarketPayOrder(aggregate));

        verify(orderDao).updateAddCompleteCount("team-1");
        verify(orderDao).updateOrderStatus2COMPLETE("team-1");
        verify(notifyTaskDao, never()).insert(any());
    }

    @Test
    void shouldRejectZeroUpdateWhenDetailIsNotCompleted() {
        GroupBuyTeamSettlementAggregate aggregate = aggregate();
        when(orderListDao.updateOrderStatus2COMPLETE(any())).thenReturn(0);
        when(orderListDao.queryGroupBuyOrderRecordByOutTradeNo(any()))
                .thenReturn(GroupBuyOrderList.builder().teamId("team-1").status(2).build());

        AppException exception = assertThrows(
                AppException.class,
                () -> repository.settlementMarketPayOrder(aggregate)
        );

        assertEquals(ResponseCode.UPDATE_ZERO.getCode(), exception.getCode());
        verify(orderDao, never()).updateAddCompleteCount("team-1");
    }

    @Test
    void shouldCreateOneNotifyTaskOnlyWhenTeamFirstCompletes() {
        GroupBuyTeamSettlementAggregate aggregate = aggregate(true);
        when(orderListDao.updateOrderStatus2COMPLETE(any())).thenReturn(1);
        when(orderDao.updateAddCompleteCount("team-1")).thenReturn(1);
        when(orderDao.updateOrderStatus2COMPLETE("team-1")).thenReturn(1);
        when(orderListDao.queryGroupBuyCompleteOrderOutTradeNoListByTeamId("team-1"))
                .thenReturn(List.of("order-1", "order-2"));

        NotifyTaskEntity result = repository.settlementMarketPayOrder(aggregate);

        assertNotNull(result);
        assertEquals("team-1", result.getTeamId());
        assertTrue(result.getParameterJson().contains("order-1"));
        assertTrue(result.getParameterJson().contains("order-2"));

        ArgumentCaptor<NotifyTask> taskCaptor = forClass(NotifyTask.class);
        verify(notifyTaskDao).insert(taskCaptor.capture());
        assertEquals("HTTP", taskCaptor.getValue().getNotifyType());
        assertEquals("http://127.0.0.1:8092/api/v1/alipay/group_buy_notify",
                taskCaptor.getValue().getNotifyUrl());
    }

    private GroupBuyTeamSettlementAggregate aggregate() {
        return aggregate(false);
    }

    private GroupBuyTeamSettlementAggregate aggregate(boolean withNotifyConfig) {
        return GroupBuyTeamSettlementAggregate.builder()
                .userEntity(UserEntity.builder().userId("1001").build())
                .groupBuyTeamEntity(GroupBuyTeamEntity.builder()
                        .activityId(10001L)
                        .teamId("team-1")
                        .targetCount(3)
                        .completeCount(1)
                        .lockCount(3)
                        .notifyConfigVO(withNotifyConfig
                                ? NotifyConfigVO.builder()
                                        .notifyType(NotifyTypeEnumVO.HTTP)
                                        .notifyUrl("http://127.0.0.1:8092/api/v1/alipay/group_buy_notify")
                                        .build()
                                : null)
                        .build())
                .tradePaySuccessEntity(TradePaySuccessEntity.builder()
                        .userId("1001")
                        .outTradeNo("order-1")
                        .outTradeTime(new Date())
                        .build())
                .build();
    }
}
