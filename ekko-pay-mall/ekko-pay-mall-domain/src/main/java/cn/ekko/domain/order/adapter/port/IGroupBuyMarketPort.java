package cn.ekko.domain.order.adapter.port;

import cn.ekko.domain.order.model.entity.MarketPayDiscountEntity;

import java.util.Date;

/**
 * 拼团营销服务端口。
 *
 * Domain 只依赖该接口，具体 HTTP/Retrofit 调用由 Infrastructure 实现。
 */
public interface IGroupBuyMarketPort {

    MarketPayDiscountEntity lockMarketPayOrder(
            String userId,
            String teamId,
            Long activityId,
            String goodsId,
            String outTradeNo
    );

    /**
     * 将商城已确认的支付事实结算到拼团营销服务。
     */
    void settlementMarketPayOrder(String userId, String outTradeNo, Date outTradeTime);

    /** 取消拼团明细和人数占用；这里只处理退单，不执行支付宝退款。 */
    void refundMarketPayOrder(String userId, String outTradeNo);
}
