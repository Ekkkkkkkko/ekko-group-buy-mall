package cn.ekko.domain.order.adapter.repository;

import cn.ekko.domain.order.model.aggregate.CreateOrderAggregate;
import cn.ekko.domain.order.model.entity.OrderEntity;
import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.entity.ShopCartEntity;

import java.util.List;
import java.util.Date;

/**
 * 订单仓储服务 —— domain 领域层就像一个饭点的厨师，他需要的各种材料，米、面、粮、油、水，都不是它生产的，它只是知道要做啥，要用啥，用通过管道【接口】把这些东西传递进来
 */
public interface IOrderRepository {

    /**
     * 查询未支付订单
     *
     * @param shopCartEntity 购物车实体对象
     * @return 订单实体对象
     */
    OrderEntity queryUnPayOrder(ShopCartEntity shopCartEntity);

    /**
     * 保存订单对象
     *
     * @param orderAggregate 订单聚合
     */
    void doSaveOrder(CreateOrderAggregate orderAggregate);

    /**
     * 保存拼团锁单返回的价格快照和队伍信息。
     */
    void updateOrderMarketInfo(OrderEntity orderEntity);

    /**
     * 更新订单支付信息
     *
     * @param payOrderEntity 支付单
     */
    void updateOrderPayInfo(PayOrderEntity payOrderEntity);

    /**
     * 按商城订单号查询本地支付订单。
     */
    PayOrderEntity queryOrderByOrderId(String orderId);

    /**
     * 订单支付成功
     * @param orderId 订单ID
     */
    int changeOrderPaySuccess(String orderId, Date payTime);

    /**
     * 锁定回调涉及的商城订单，并批量将 PAY_SUCCESS 拼团订单更新为 MARKET。
     * 返回值保留更新前状态，其中 PAY_SUCCESS 项就是本次首次更新成功的订单。
     */
    List<PayOrderEntity> changeOrderMarketSettlement(String teamId, List<String> outTradeNoList);

    int changeOrderDealDone(String orderId);

    /**
     * 发布普通订单首次支付成功事件。
     */
    void publishPaySuccessEvent(String userId, String orderId);

    void publishMarketSettlementEvent(String userId, String orderId);

    /**
     * 记录拼团服务已经确认该商城订单的成员结算。
     */
    void markMarketSettlementCompleted(String orderId);

    /**
     * 查询已支付但尚无拼团结算确认标记的订单。
     */
    List<PayOrderEntity> queryPendingMarketSettlementOrders();

    List<String> queryNoPayNotifyOrder();

    List<String> queryTimeoutCloseOrderList();

    boolean changeOrderClose(String orderId);

}
