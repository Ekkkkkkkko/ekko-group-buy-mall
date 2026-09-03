package cn.ekko.domain.order.service;

import cn.ekko.domain.order.adapter.port.IGroupBuyMarketPort;
import cn.ekko.domain.order.adapter.port.IProductPort;
import cn.ekko.domain.order.adapter.repository.IOrderRepository;
import cn.ekko.domain.order.model.aggregate.CreateOrderAggregate;
import cn.ekko.domain.order.model.entity.MarketPayDiscountEntity;
import cn.ekko.domain.order.model.entity.OrderEntity;
import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.entity.ProductEntity;
import cn.ekko.domain.order.model.entity.ShopCartEntity;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import com.alipay.api.AlipayApiException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * 通过抽象类来定义下单的标准
 */
@Slf4j
public abstract class AbstractOrderService implements IOrderService {

    protected final IOrderRepository repository;
    protected final IProductPort productPort;
    protected final IGroupBuyMarketPort groupBuyMarketPort;

    public AbstractOrderService(IOrderRepository repository,
                                IProductPort productPort,
                                IGroupBuyMarketPort groupBuyMarketPort) {
        this.repository = repository;
        this.productPort = productPort;
        this.groupBuyMarketPort = groupBuyMarketPort;
    }

    @Override
    public PayOrderEntity createOrder(ShopCartEntity shopCartEntity) throws Exception {
        // 1. 查询当前用户是否存在掉单和未支付订单
        OrderEntity unpaidOrderEntity = repository.queryUnPayOrder(shopCartEntity);
        if (null != unpaidOrderEntity && OrderStatusVO.PAY_WAIT.equals(unpaidOrderEntity.getOrderStatus())) {
            log.info("创建订单-存在，已存在未支付订单。userId:{} productId:{} orderId:{}", shopCartEntity.getUserId(), shopCartEntity.getProductId(), unpaidOrderEntity.getOrderId());
            return PayOrderEntity.builder()
                    .orderId(unpaidOrderEntity.getOrderId())
                    .payUrl(unpaidOrderEntity.getPayUrl())
                    .orderStatus(unpaidOrderEntity.getOrderStatus())
                    .marketType(unpaidOrderEntity.getMarketType())
                    .activityId(unpaidOrderEntity.getActivityId())
                    .teamId(unpaidOrderEntity.getTeamId())
                    .marketDeductionAmount(unpaidOrderEntity.getMarketDeductionAmount())
                    .payAmount(unpaidOrderEntity.getPayAmount())
                    .build();
        } else if (null != unpaidOrderEntity && OrderStatusVO.CREATE.equals(unpaidOrderEntity.getOrderStatus())) {
            log.info("创建订单-存在，存在未创建支付单订单，创建支付单开始 userId:{} productId:{} orderId:{}", shopCartEntity.getUserId(), shopCartEntity.getProductId(), unpaidOrderEntity.getOrderId());
            return createPayOrder(shopCartEntity.getUserId(), unpaidOrderEntity);
        }

        // 2. 查询商品 & 聚合订单
        ProductEntity productEntity = productPort.queryProductByProductId(shopCartEntity.getProductId());

        OrderEntity orderEntity = CreateOrderAggregate.buildOrderEntity(productEntity, shopCartEntity);

        CreateOrderAggregate orderAggregate = CreateOrderAggregate.builder()
                .userId(shopCartEntity.getUserId())
                .productEntity(productEntity)
                .orderEntity(orderEntity)
                .build();

        // 3. 保存订单 - 保存一份订单，再用订单生成ID生成支付单信息
        this.doSaveOrder(orderAggregate);

        // 4. 创建支付单
        PayOrderEntity payOrderEntity = createPayOrder(shopCartEntity.getUserId(), orderEntity);
        log.info("创建订单-完成，生成支付单。userId: {} orderId: {} payUrl: {}", shopCartEntity.getUserId(), orderEntity.getOrderId(), payOrderEntity.getPayUrl());

        return payOrderEntity;
    }

    private PayOrderEntity createPayOrder(String userId, OrderEntity orderEntity) throws AlipayApiException {
        try {
            BigDecimal payAmount = preparePayAmount(userId, orderEntity);

            PayOrderEntity payOrderEntity = this.doPrepayOrder(
                    userId,
                    orderEntity.getProductId(),
                    orderEntity.getProductName(),
                    orderEntity.getOrderId(),
                    payAmount
            );
            payOrderEntity.setMarketType(orderEntity.getMarketType());
            payOrderEntity.setActivityId(orderEntity.getActivityId());
            payOrderEntity.setTeamId(orderEntity.getTeamId());
            payOrderEntity.setMarketDeductionAmount(orderEntity.getMarketDeductionAmount());
            payOrderEntity.setPayAmount(payAmount);
            return payOrderEntity;
        } catch (AppException e) {
            closeRejectedGroupBuyOrder(userId, orderEntity, e);
            throw e;
        }
    }

    /**
     * 拼团服务明确返回业务拒绝时，远端没有成功锁单，本地 CREATE 订单不能继续等待重试。
     * 网络异常和响应不确定场景不在这里关单，保留相同订单号供幂等重试或人工补偿。
     */
    private void closeRejectedGroupBuyOrder(String userId, OrderEntity orderEntity, AppException exception) {
        if (!MarketTypeVO.GROUP_BUY_MARKET.equals(orderEntity.getMarketType())
                || !ResponseCode.GROUP_BUY_BUSINESS_ERROR.getCode().equals(exception.getCode())
                || !OrderStatusVO.CREATE.equals(orderEntity.getOrderStatus())) {
            return;
        }

        int updateCount = repository.refundOrder(userId, orderEntity.getOrderId(), OrderStatusVO.CREATE);
        if (1 == updateCount) {
            log.info("拼团锁单被业务拒绝，本地CREATE订单已关闭 userId:{} orderId:{}",
                    userId, orderEntity.getOrderId());
        } else {
            log.warn("拼团锁单被业务拒绝，但本地CREATE订单关闭影响行数异常 userId:{} orderId:{} updateCount:{}",
                    userId, orderEntity.getOrderId(), updateCount);
        }
    }

    private BigDecimal preparePayAmount(String userId, OrderEntity orderEntity) {
        MarketTypeVO marketType = null == orderEntity.getMarketType()
                ? MarketTypeVO.NO_MARKET
                : orderEntity.getMarketType();

        if (MarketTypeVO.GROUP_BUY_MARKET.equals(marketType) && null == orderEntity.getPayAmount()) {
            MarketPayDiscountEntity marketPayDiscountEntity = groupBuyMarketPort.lockMarketPayOrder(
                    userId,
                    orderEntity.getTeamId(),
                    orderEntity.getActivityId(),
                    orderEntity.getProductId(),
                    orderEntity.getOrderId()
            );
            orderEntity.applyMarketDiscount(marketPayDiscountEntity);
            repository.updateOrderMarketInfo(orderEntity);
        }

        BigDecimal payAmount = null == orderEntity.getPayAmount()
                ? orderEntity.getTotalAmount()
                : orderEntity.getPayAmount();
        if (null == payAmount || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(
                    ResponseCode.ORDER_PAY_AMOUNT_ERROR.getCode(),
                    ResponseCode.ORDER_PAY_AMOUNT_ERROR.getInfo()
            );
        }
        return payAmount;
    }

    /**
     * 保存订单
     *
     * @param orderAggregate 订单聚合
     */
    protected abstract void doSaveOrder(CreateOrderAggregate orderAggregate);

    /**
     * 预支付订单生成
     *
     * @param userId      用户ID
     * @param productId   商品ID
     * @param productName 商品名称
     * @param orderId     订单ID
     * @param payAmount   实际支付金额
     * @return 预支付订单
     */
    protected abstract PayOrderEntity doPrepayOrder(String userId, String productId, String productName, String orderId, BigDecimal payAmount) throws AlipayApiException;

}
