package cn.ekko.domain.order.adapter.repository;

import cn.ekko.domain.order.model.aggregate.CreateOrderAggregate;
import cn.ekko.domain.order.model.entity.OrderEntity;
import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.entity.ShopCartEntity;

import java.util.List;

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
     * 更新订单支付信息
     *
     * @param payOrderEntity 支付单
     */
    void updateOrderPayInfo(PayOrderEntity payOrderEntity);

    /**
     * 订单支付成功
     * @param orderId 订单ID
     */
    void changeOrderPaySuccess(String orderId);

    List<String> queryNoPayNotifyOrder();

    List<String> queryTimeoutCloseOrderList();

    boolean changeOrderClose(String orderId);

}
