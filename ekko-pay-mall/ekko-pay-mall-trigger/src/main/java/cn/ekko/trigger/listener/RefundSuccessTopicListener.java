package cn.ekko.trigger.listener;

import cn.ekko.api.dto.TeamRefundSuccessRequestDTO;
import cn.ekko.domain.order.service.IOrderService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 商城退款队列消费者。任何处理失败都必须抛出，由 RabbitMQ 重试并最终进入死信队列。
 */
@Slf4j
@Component
public class RefundSuccessTopicListener {

    private static final String UNPAID_UNLOCK = "unpaid_unlock";
    private static final String PAID_UNFORMED = "paid_unformed";
    private static final String PAID_FORMED = "paid_formed";

    @Resource
    private IOrderService orderService;

    @RabbitListener(queues = "${spring.rabbitmq.config.consumer.topic_team_refund.queue}")
    public void listener(String message) {
        try {
            TeamRefundSuccessRequestDTO request = JSON.parseObject(message, TeamRefundSuccessRequestDTO.class);
            validate(request);

            switch (request.getType()) {
                case UNPAID_UNLOCK -> orderService.confirmUnpaidRefundOrderClosed(
                        request.getUserId(), request.getOutTradeNo());
                case PAID_UNFORMED, PAID_FORMED -> orderService.refundPayOrder(
                        request.getUserId(), request.getOutTradeNo());
                default -> throw new IllegalArgumentException("不支持的拼团退款消息类型: " + request.getType());
            }

            log.info("商城退款消息处理成功 type:{} userId:{} outTradeNo:{} teamId:{}",
                    request.getType(), request.getUserId(), request.getOutTradeNo(), request.getTeamId());
        } catch (Exception e) {
            log.error("商城退款消息处理失败，抛出异常触发RabbitMQ重试 message:{}", message, e);
            throw new IllegalStateException("商城退款消息处理失败", e);
        }
    }

    private void validate(TeamRefundSuccessRequestDTO request) {
        if (null == request
                || isBlank(request.getType())
                || isBlank(request.getUserId())
                || isBlank(request.getTeamId())
                || isBlank(request.getOrderId())
                || isBlank(request.getOutTradeNo())
                || null == request.getActivityId()) {
            throw new IllegalArgumentException("拼团退款消息缺少必填字段");
        }
    }

    private boolean isBlank(String value) {
        return null == value || value.isBlank();
    }
}
