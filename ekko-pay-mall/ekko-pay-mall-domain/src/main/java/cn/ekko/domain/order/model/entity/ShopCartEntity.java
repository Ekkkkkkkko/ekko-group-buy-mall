package cn.ekko.domain.order.model.entity;

import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 购物车实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShopCartEntity {

    /** 用户ID */
    private String userId;

    /** 商品ID */
    private String productId;
    private String teamId;
    private Long activityId;
    private MarketTypeVO marketType;

}
