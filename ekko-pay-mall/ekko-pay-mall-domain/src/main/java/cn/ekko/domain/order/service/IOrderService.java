package cn.ekko.domain.order.service;

import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.entity.ShopCartEntity;

import java.util.List;
import java.util.Date;

/**
 * 订单服务
 */
public interface IOrderService {

    /**
     * 通过购物车实体对象，创建支付单实体（用于支付）—— 所有的订单下单都从购物车开始触发
     *
     * @param shopCartEntity 购物车实体
     * @return 支付单实体
     */
    PayOrderEntity createOrder(ShopCartEntity shopCartEntity) throws Exception;

    /**
     * 更新订单状态
     * @param orderId 订单ID
     */
    boolean changeOrderPaySuccess(String orderId, Date payTime);

    /**
     * 接收拼团成团通知，并只为首次从 PAY_SUCCESS 进入 MARKET 的订单发布履约事件。
     */
    boolean groupBuyNotify(String teamId, List<String> outTradeNoList);

    /**
     * 商品履约完成后，将拼团订单从 MARKET 更新为 DEAL_DONE。
     */
    boolean changeOrderDealDone(String orderId);

    /**
     * 查询需要重试拼团结算的已支付订单。
     */
    List<PayOrderEntity> queryPendingMarketSettlementOrders();

    /**
     * 查询有效期内，未接收到支付回调的订单
     */
    List<String> queryNoPayNotifyOrder();

    /**
     * 查询超时15分钟，未支付订单
     */
    List<String> queryTimeoutCloseOrderList();

    boolean changeOrderClose(String orderId);

}
