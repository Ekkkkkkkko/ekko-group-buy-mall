package cn.ekko.trigger.job;

import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 退款补偿：扫描长时间WAIT_REFUND订单。refundPayOrder会先查支付宝，再决定是否使用同一退款号重试。
 */
@Slf4j
@Component
public class RefundCompensationJob {

    @Resource
    private IOrderService orderService;

    @Scheduled(cron = "${refund.compensation-cron:0/30 * * * * ?}")
    public void exec() {
        List<PayOrderEntity> orders = orderService.queryTimeoutWaitRefundOrders();
        if (null == orders || orders.isEmpty()) return;

        for (PayOrderEntity order : orders) {
            try {
                orderService.refundPayOrder(order.getUserId(), order.getOrderId());
            } catch (Exception e) {
                // 保持WAIT_REFUND，下一轮继续先查询支付宝；日志用于人工定位和对账。
                log.error("退款补偿失败，订单保持WAIT_REFUND userId:{} outTradeNo:{} payAmount:{}",
                        order.getUserId(), order.getOrderId(), order.getPayAmount(), e);
            }
        }
    }
}
