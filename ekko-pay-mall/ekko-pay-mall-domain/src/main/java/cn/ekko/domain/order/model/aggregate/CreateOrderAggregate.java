package cn.ekko.domain.order.model.aggregate;

import cn.ekko.domain.order.model.entity.OrderEntity;
import cn.ekko.domain.order.model.entity.ProductEntity;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

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

    public static OrderEntity buildOrderEntity(String productId, String productName) {
        return OrderEntity.builder()
                .productId(productId)
                .productName(productName)
                .orderId(RandomStringUtils.randomNumeric(16))
                .orderTime(new Date())
                .orderStatus(OrderStatusVO.CREATE)
                .build();
    }

}
