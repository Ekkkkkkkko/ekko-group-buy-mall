package cn.ekko.domain.product.adapter.port;

import cn.ekko.domain.order.model.entity.GroupBuyMarketEntity;

/**
 * 商品展示侧查询拼团优惠的端口。
 */
public interface IGroupBuyMarketQueryPort {

    GroupBuyMarketEntity queryGroupBuyMarket(String userId, String productId);
}
