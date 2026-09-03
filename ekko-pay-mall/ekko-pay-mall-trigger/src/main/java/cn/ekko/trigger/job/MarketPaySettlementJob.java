package cn.ekko.trigger.job;

import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 补偿已支付但尚未得到拼团服务确认的成员结算。
 */
@Slf4j
@Component
public class MarketPaySettlementJob {

    @Resource
    private IOrderService orderService;

    @Scheduled(cron = "0/10 * * * * ?")
    public void exec() {
        List<PayOrderEntity> pendingOrders;
        try {
            pendingOrders = orderService.queryPendingMarketSettlementOrders();
        } catch (Exception e) {
            log.error("查询待补偿拼团结算订单失败", e);
            return;
        }

        for (PayOrderEntity order : pendingOrders) {
            try {
                boolean handled = orderService.changeOrderPaySuccess(order.getOrderId(), order.getPayTime());
                if (!handled) {
                    log.warn("拼团结算补偿停止，订单状态不允许继续处理 orderId:{}", order.getOrderId());
                }
            } catch (Exception e) {
                log.error("拼团结算补偿失败，等待下次重试 orderId:{} userId:{}",
                        order.getOrderId(), order.getUserId(), e);
            }
        }
    }
}
