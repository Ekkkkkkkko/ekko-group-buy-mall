package cn.ekko.trigger.listener;

import cn.ekko.domain.goods.service.IGoodsService;
import cn.ekko.domain.order.event.PaySuccessMessageEvent;
import com.alibaba.fastjson.JSON;
import com.google.common.eventbus.Subscribe;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 支付成功回调消息
 */
@Slf4j
@Component
public class OrderPaySuccessListener {

    @Resource
    @Lazy
    private IGoodsService goodsService;

    @Subscribe
    public void handleEvent(String paySuccessMessage) {
        PaySuccessMessageEvent.PaySuccessMessage message = JSON.parseObject(
                paySuccessMessage,
                PaySuccessMessageEvent.PaySuccessMessage.class
        );
        if (!Boolean.TRUE.equals(message.getMarketSettlement())) {
            log.info("收到普通订单支付成功消息，保留原教学入口 paySuccessMessage:{}", paySuccessMessage);
            return;
        }

        log.info("收到拼团成团履约事件 userId:{} orderId:{}", message.getUserId(), message.getTradeNo());
        goodsService.deliverGoods(message.getUserId(), message.getTradeNo());
    }

}
