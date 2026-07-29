package cn.ekko.infrastructure.adapter.repository;

import cn.ekko.domain.order.adapter.repository.IOrderRepository;
import cn.ekko.domain.order.event.PaySuccessMessageEvent;
import cn.ekko.domain.order.model.aggregate.CreateOrderAggregate;
import cn.ekko.domain.order.model.entity.OrderEntity;
import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.entity.ProductEntity;
import cn.ekko.domain.order.model.entity.ShopCartEntity;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import cn.ekko.infrastructure.dao.IOrderDao;
import cn.ekko.infrastructure.dao.po.PayOrder;
import cn.ekko.infrastructure.redis.IRedisService;
import cn.ekko.types.event.BaseEvent;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import com.google.common.eventbus.EventBus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单仓储实现
 */
@Repository
public class OrderRepository implements IOrderRepository {

    private static final String MARKET_SETTLEMENT_COMPLETED_KEY_PREFIX = "pay_order:market_settlement:completed:";
    private static final long MARKET_SETTLEMENT_MARK_TTL_MILLIS = TimeUnit.DAYS.toMillis(30);

    @Resource
    private IOrderDao orderDao;
    @Resource
    private IRedisService redisService;
    @Resource
    private EventBus eventBus;
    @Resource
    private PaySuccessMessageEvent paySuccessMessageEvent;

    @Override
    public OrderEntity queryUnPayOrder(ShopCartEntity shopCartEntity) {
        // 1. 封装参数
        PayOrder orderReq = new PayOrder();
        orderReq.setUserId(shopCartEntity.getUserId());
        orderReq.setProductId(shopCartEntity.getProductId());
        // 2. 查询到订单
        PayOrder order = orderDao.queryUnPayOrder(orderReq);
        if (null == order) return null;
        // 3. 返回结果
        return OrderEntity.builder()
                .productId(order.getProductId())
                .productName(order.getProductName())
                .orderId(order.getOrderId())
                .orderStatus(OrderStatusVO.valueOf(order.getStatus()))
                .orderTime(order.getOrderTime())
                .totalAmount(order.getTotalAmount())
                .payUrl(order.getPayUrl())
                .marketType(MarketTypeVO.valueOf(order.getMarketType()))
                .activityId(order.getActivityId())
                .teamId(order.getTeamId())
                .marketDeductionAmount(order.getMarketDeductionAmount())
                .payAmount(order.getPayAmount())
                .build();
    }

    @Override
    public void doSaveOrder(CreateOrderAggregate orderAggregate) {
        String userId = orderAggregate.getUserId();
        ProductEntity productEntity = orderAggregate.getProductEntity();
        OrderEntity orderEntity = orderAggregate.getOrderEntity();

        PayOrder order = new PayOrder();
        order.setUserId(userId);
        order.setProductId(productEntity.getProductId());
        order.setProductName(productEntity.getProductName());
        order.setOrderId(orderEntity.getOrderId());
        order.setOrderTime(orderEntity.getOrderTime());
        order.setTotalAmount(orderEntity.getTotalAmount());
        order.setStatus(orderEntity.getOrderStatus().getCode());
        order.setMarketType(orderEntity.getMarketType().getCode());
        order.setActivityId(orderEntity.getActivityId());
        order.setTeamId(orderEntity.getTeamId());
        order.setMarketDeductionAmount(orderEntity.getMarketDeductionAmount());
        order.setPayAmount(orderEntity.getPayAmount());

        orderDao.insert(order);

        // 存入缓存；缓存key聚合到对象中提供
        redisService.setValue(PayOrder.cacheKey(userId, orderEntity.getOrderId()), order);
    }

    @Override
    public void updateOrderMarketInfo(OrderEntity orderEntity) {
        PayOrder order = PayOrder.builder()
                .orderId(orderEntity.getOrderId())
                .totalAmount(orderEntity.getTotalAmount())
                .marketType(orderEntity.getMarketType().getCode())
                .activityId(orderEntity.getActivityId())
                .teamId(orderEntity.getTeamId())
                .marketDeductionAmount(orderEntity.getMarketDeductionAmount())
                .payAmount(orderEntity.getPayAmount())
                .build();
        int updateCount = orderDao.updateOrderMarketInfo(order);
        if (1 != updateCount) {
            throw new AppException(
                    ResponseCode.UN_ERROR.getCode(),
                    "保存订单营销锁单结果失败"
            );
        }
    }

    @Override
    public void updateOrderPayInfo(PayOrderEntity payOrderEntity) {
        PayOrder order = new PayOrder();
        order.setUserId(payOrderEntity.getUserId());
        order.setOrderId(payOrderEntity.getOrderId());
        order.setPayUrl(payOrderEntity.getPayUrl());
        order.setStatus(payOrderEntity.getOrderStatus().getCode());
        int updateCount = orderDao.updateOrderPayInfo(order);
        if (1 != updateCount) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "支付信息更新失败，订单状态已变化");
        }
    }

    @Override
    public PayOrderEntity queryOrderByOrderId(String orderId) {
        PayOrder order = orderDao.queryOrderByOrderId(orderId);
        return toPayOrderEntity(order);
    }

    @Override
    public PayOrderEntity queryOrderByUserIdAndOrderId(String userId, String orderId) {
        return toPayOrderEntity(orderDao.queryOrderByUserIdAndOrderId(userId, orderId));
    }

    @Override
    public List<PayOrderEntity> queryUserOrderList(String userId, Long lastId, int limit) {
        return orderDao.queryUserOrderList(userId, lastId, limit).stream()
                .map(this::toPayOrderEntity)
                .collect(Collectors.toList());
    }

    @Override
    public int refundMarketOrder(String userId, String orderId, OrderStatusVO expectedStatus) {
        return orderDao.refundMarketOrder(userId, orderId, expectedStatus.getCode());
    }

    @Override
    public int refundOrder(String userId, String orderId, OrderStatusVO expectedStatus) {
        return orderDao.refundOrder(userId, orderId, expectedStatus.getCode());
    }

    @Override
    public int changeOrderPaySuccess(String orderId, Date payTime) {
        PayOrder order = new PayOrder();
        order.setOrderId(orderId);
        order.setStatus(OrderStatusVO.PAY_SUCCESS.getCode());
        order.setPayTime(payTime);
        return orderDao.changeOrderPaySuccess(order);
    }

    @Override
    @Transactional(timeout = 5)
    public List<PayOrderEntity> changeOrderMarketSettlement(String teamId, List<String> outTradeNoList) {
        List<PayOrder> matchedOrders = orderDao.queryMarketOrdersByTeamIdAndOrderIds(teamId, outTradeNoList);
        List<String> firstMarketOrderIds = matchedOrders.stream()
                .filter(order -> OrderStatusVO.PAY_SUCCESS.getCode().equals(order.getStatus()))
                .map(PayOrder::getOrderId)
                .collect(Collectors.toList());

        if (!firstMarketOrderIds.isEmpty()) {
            int updateCount = orderDao.changeOrderMarketSettlement(teamId, firstMarketOrderIds);
            if (updateCount != firstMarketOrderIds.size()) {
                throw new AppException(
                        ResponseCode.UN_ERROR.getCode(),
                        "成团订单更新影响行数异常，expected=" + firstMarketOrderIds.size() + " actual=" + updateCount
                );
            }
        }

        return matchedOrders.stream()
                .map(order -> PayOrderEntity.builder()
                        .userId(order.getUserId())
                        .orderId(order.getOrderId())
                        .orderStatus(OrderStatusVO.valueOf(order.getStatus()))
                        .marketType(MarketTypeVO.valueOf(order.getMarketType()))
                        .teamId(order.getTeamId())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public int changeOrderDealDone(String orderId) {
        return orderDao.changeOrderDealDone(orderId);
    }

    @Override
    public void publishPaySuccessEvent(String userId, String orderId) {
        publishOrderEvent(userId, orderId, false);
    }

    @Override
    public void publishMarketSettlementEvent(String userId, String orderId) {
        publishOrderEvent(userId, orderId, true);
    }

    private void publishOrderEvent(String userId, String orderId, boolean marketSettlement) {
        BaseEvent.EventMessage<PaySuccessMessageEvent.PaySuccessMessage> eventMessage =
                paySuccessMessageEvent.buildEventMessage(
                        PaySuccessMessageEvent.PaySuccessMessage.builder()
                                .userId(userId)
                                .tradeNo(orderId)
                                .marketSettlement(marketSettlement)
                                .build()
                );
        eventBus.post(JSON.toJSONString(eventMessage.getData()));
    }

    @Override
    public void markMarketSettlementCompleted(String orderId) {
        redisService.setValue(
                MARKET_SETTLEMENT_COMPLETED_KEY_PREFIX + orderId,
                Boolean.TRUE,
                MARKET_SETTLEMENT_MARK_TTL_MILLIS
        );
    }

    @Override
    public List<PayOrderEntity> queryPendingMarketSettlementOrders() {
        return orderDao.queryPaidMarketOrders().stream()
                .filter(order -> !redisService.isExists(MARKET_SETTLEMENT_COMPLETED_KEY_PREFIX + order.getOrderId()))
                .map(order -> PayOrderEntity.builder()
                        .userId(order.getUserId())
                        .orderId(order.getOrderId())
                        .orderStatus(OrderStatusVO.valueOf(order.getStatus()))
                        .marketType(MarketTypeVO.valueOf(order.getMarketType()))
                        .activityId(order.getActivityId())
                        .teamId(order.getTeamId())
                        .payTime(order.getPayTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> queryNoPayNotifyOrder() {
        return orderDao.queryNoPayNotifyOrder();
    }

    @Override
    public List<String> queryTimeoutCloseOrderList() {
        return orderDao.queryTimeoutCloseOrderList();
    }

    @Override
    public boolean changeOrderClose(String orderId) {
        int updateCount = orderDao.changeOrderClose(orderId);
        if (updateCount > 1) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "关单更新影响行数异常：" + updateCount);
        }
        return 1 == updateCount;
    }

    private PayOrderEntity toPayOrderEntity(PayOrder order) {
        if (null == order) return null;
        return PayOrderEntity.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .productId(order.getProductId())
                .productName(order.getProductName())
                .orderId(order.getOrderId())
                .orderTime(order.getOrderTime())
                .totalAmount(order.getTotalAmount())
                .payUrl(order.getPayUrl())
                .orderStatus(OrderStatusVO.valueOf(order.getStatus()))
                .marketType(MarketTypeVO.valueOf(order.getMarketType()))
                .activityId(order.getActivityId())
                .teamId(order.getTeamId())
                .marketDeductionAmount(order.getMarketDeductionAmount())
                .payAmount(order.getPayAmount())
                .payTime(order.getPayTime())
                .build();
    }

}
