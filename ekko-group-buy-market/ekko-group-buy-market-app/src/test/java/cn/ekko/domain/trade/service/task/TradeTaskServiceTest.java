package cn.ekko.domain.trade.service.task;

import cn.ekko.domain.trade.adapter.port.ITradePort;
import cn.ekko.domain.trade.adapter.repository.ITradeRepository;
import cn.ekko.domain.trade.model.entity.NotifyTaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeTaskServiceTest {

    private ITradeRepository repository;
    private ITradePort port;
    private TradeTaskService service;

    @BeforeEach
    void setUp() {
        repository = mock(ITradeRepository.class);
        port = mock(ITradePort.class);
        service = new TradeTaskService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "port", port);
    }

    @Test
    void shouldSendOnlyAfterClaimingSpecificTask() throws Exception {
        NotifyTaskEntity task = task(0);
        when(repository.claimNotifyTask(task)).thenReturn(0);

        service.execNotifyJob(task);

        verify(port, never()).groupBuyNotify(task);
        verify(repository, never()).updateNotifyTaskStatusSuccess(task);
    }

    @Test
    void shouldMarkClaimedTaskSuccess() throws Exception {
        NotifyTaskEntity task = task(0);
        when(repository.claimNotifyTask(task)).thenReturn(1);
        when(port.groupBuyNotify(task)).thenReturn("success");
        when(repository.updateNotifyTaskStatusSuccess(task)).thenReturn(1);

        service.execNotifyJob(task);

        verify(repository).updateNotifyTaskStatusSuccess(task);
        verify(repository, never()).updateNotifyTaskStatusRetry(task);
    }

    @Test
    void shouldMoveFailedTaskToRetry() throws Exception {
        NotifyTaskEntity task = task(0);
        when(repository.claimNotifyTask(task)).thenReturn(1);
        when(port.groupBuyNotify(task)).thenReturn("error");
        when(repository.updateNotifyTaskStatusRetry(task)).thenReturn(1);

        service.execNotifyJob(task);

        verify(repository).updateNotifyTaskStatusRetry(task);
        verify(repository, never()).updateNotifyTaskStatusError(task);
        assertEquals("notify response: error", task.getLastError());
    }

    @Test
    void shouldMarkFifthFailureAsFinalError() throws Exception {
        NotifyTaskEntity task = task(4);
        when(repository.claimNotifyTask(task)).thenReturn(1);
        when(port.groupBuyNotify(task)).thenReturn("error");
        when(repository.updateNotifyTaskStatusError(task)).thenReturn(1);

        service.execNotifyJob(task);

        verify(repository).updateNotifyTaskStatusError(task);
        verify(repository, never()).updateNotifyTaskStatusRetry(task);
    }

    private NotifyTaskEntity task(int notifyCount) {
        return NotifyTaskEntity.builder()
                .teamId("team-1")
                .uuid("team-1_trade_settlement_order-1")
                .notifyType("HTTP")
                .notifyUrl("http://127.0.0.1/callback")
                .notifyCount(notifyCount)
                .parameterJson("{}")
                .build();
    }

}
