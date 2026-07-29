package cn.ekko.domain.order.model.aggregate;

import cn.ekko.domain.order.model.entity.OrderEntity;
import cn.ekko.domain.order.model.entity.ProductEntity;
import cn.ekko.domain.order.model.entity.ShopCartEntity;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderAggregate {

    // 用户ID
    private String userId;
    // 商品实体对象
    private ProductEntity productEntity;
    // 订单实体对象
    private OrderEntity orderEntity;

    public static OrderEntity buildOrderEntity(ProductEntity productEntity, ShopCartEntity shopCartEntity) {
        MarketTypeVO marketType = null == shopCartEntity.getMarketType()
                ? MarketTypeVO.NO_MARKET
                : shopCartEntity.getMarketType();
        boolean groupBuy = MarketTypeVO.GROUP_BUY_MARKET.equals(marketType);

        return OrderEntity.builder()
                .productId(productEntity.getProductId())
                .productName(productEntity.getProductName())
                .orderId(RandomStringUtils.randomNumeric(16))
                .orderTime(new Date())
                .orderStatus(OrderStatusVO.CREATE)
                .totalAmount(productEntity.getPrice())
                .marketType(marketType)
                .activityId(shopCartEntity.getActivityId())
                .teamId(shopCartEntity.getTeamId())
                .marketDeductionAmount(groupBuy ? null : BigDecimal.ZERO)
                .payAmount(groupBuy ? null : productEntity.getPrice())
                .build();
    }

}
