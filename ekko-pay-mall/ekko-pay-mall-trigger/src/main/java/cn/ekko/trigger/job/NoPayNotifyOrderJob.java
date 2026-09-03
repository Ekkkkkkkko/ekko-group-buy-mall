package cn.ekko.trigger.job;

import cn.ekko.domain.order.service.IOrderService;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Ekko
 * @description 检测未接收到或未正确处理的支付回调通知
 */
@Slf4j
@Component()
@ConditionalOnProperty(value = "alipay.enabled", havingValue = "true")
public class NoPayNotifyOrderJob {

    private static final String SUCCESS_CODE = "10000";
    private static final String TRADE_NOT_EXIST_CODE = "ACQ.TRADE_NOT_EXIST";
    private static final long INITIAL_BACKOFF_MILLIS = 30_000L;
    private static final long MAX_BACKOFF_MILLIS = 300_000L;

    @Resource
    private IOrderService orderService;
    @Resource
    private AlipayClient alipayClient;

    private final ConcurrentMap<String, RetryState> retryStates = new ConcurrentHashMap<>();

    @Scheduled(cron = "${alipay.reconcile.cron:0/30 * * * * ?}")
    public void exec() {
        try {
            List<String> orderIds = orderService.queryNoPayNotifyOrder();
            if (null == orderIds || orderIds.isEmpty()) {
                retryStates.clear();
                return;
            }

            Set<String> activeOrderIds = new HashSet<>(orderIds);
            retryStates.keySet().removeIf(orderId -> !activeOrderIds.contains(orderId));

            for (String orderId : orderIds) {
                reconcileOrder(orderId);
            }
        } catch (Exception e) {
            log.error("查询本地待支付订单失败", e);
        }
    }

    private void reconcileOrder(String orderId) {
        long now = System.currentTimeMillis();
        RetryState retryState = retryStates.get(orderId);
        if (null != retryState && now < retryState.nextAttemptAtMillis()) {
            return;
        }

        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel bizModel = new AlipayTradeQueryModel();
            bizModel.setOutTradeNo(orderId);
            request.setBizModel(bizModel);

            AlipayTradeQueryResponse response = alipayClient.execute(request);
            handleQueryResponse(orderId, response, now);
        } catch (Exception e) {
            RetryState nextRetry = scheduleRetry(orderId, now);
            log.warn("支付宝支付状态查询失败 orderId:{} nextRetrySeconds:{}",
                    orderId, nextRetry.remainingSeconds(now), e);
        }
    }

    private void handleQueryResponse(String orderId, AlipayTradeQueryResponse response, long now) {
        if (null == response) {
            RetryState nextRetry = scheduleRetry(orderId, now);
            log.warn("支付宝支付状态查询返回空响应 orderId:{} nextRetrySeconds:{}",
                    orderId, nextRetry.remainingSeconds(now));
            return;
        }

        String tradeStatus = response.getTradeStatus();
        Date payTime = response.getSendPayDate();
        if (SUCCESS_CODE.equals(response.getCode())
                && ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus))
                && null != payTime) {
            retryStates.remove(orderId);
            orderService.changeOrderPaySuccess(orderId, payTime);
            return;
        }

        RetryState nextRetry = scheduleRetry(orderId, now);
        if (TRADE_NOT_EXIST_CODE.equals(response.getSubCode())) {
            log.debug("支付宝交易尚未创建 orderId:{} nextRetrySeconds:{}",
                    orderId, nextRetry.remainingSeconds(now));
            return;
        }

        log.warn("支付宝支付状态暂不可用 orderId:{} code:{} subCode:{} tradeStatus:{} nextRetrySeconds:{}",
                orderId,
                response.getCode(),
                response.getSubCode(),
                tradeStatus,
                nextRetry.remainingSeconds(now));
    }

    private RetryState scheduleRetry(String orderId, long now) {
        return retryStates.compute(orderId, (key, previous) -> {
            int attempt = null == previous ? 1 : Math.min(previous.attempt() + 1, 5);
            long multiplier = 1L << (attempt - 1);
            long delayMillis = Math.min(INITIAL_BACKOFF_MILLIS * multiplier, MAX_BACKOFF_MILLIS);
            return new RetryState(attempt, now + delayMillis);
        });
    }

    private record RetryState(int attempt, long nextAttemptAtMillis) {

        private long remainingSeconds(long now) {
            return Math.max(0L, (nextAttemptAtMillis - now) / 1000L);
        }
    }

}
