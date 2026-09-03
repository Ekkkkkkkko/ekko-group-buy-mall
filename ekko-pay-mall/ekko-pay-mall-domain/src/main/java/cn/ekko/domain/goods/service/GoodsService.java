package cn.ekko.domain.goods.service;

import cn.ekko.domain.order.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 课程阶段的最小模拟履约实现。
 */
@Slf4j
@Service
public class GoodsService implements IGoodsService {

    @Resource
    private IOrderService orderService;

    @Override
    public boolean deliverGoods(String userId, String orderId) {
        log.info("模拟商品履约开始 userId:{} orderId:{} action:发货/充值/开通权益", userId, orderId);
        boolean completed = orderService.changeOrderDealDone(orderId);
        if (completed) {
            log.info("模拟商品履约完成 userId:{} orderId:{}", userId, orderId);
        } else {
            log.info("模拟商品履约跳过，订单不是首次MARKET状态 userId:{} orderId:{}", userId, orderId);
        }
        return completed;
    }

}
