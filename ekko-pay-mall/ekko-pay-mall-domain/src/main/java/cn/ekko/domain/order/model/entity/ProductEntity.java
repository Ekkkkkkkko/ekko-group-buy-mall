package cn.ekko.domain.order.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品实体对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductEntity {

    /** 商品ID */
    private String productId;
    /** 商品名称 */
    private String productName;
    /** 商品描述 */
    private String productDesc;
    /** 路由器型号 */
    private String productModel;
    /** 简单参数描述 */
    private String productSpecs;
    /** 商品价格 */
    private BigDecimal price;
    /** 固定OSS图片地址 */
    private String imageUrl;
    /** 上下架状态：0下架、1上架 */
    private Integer status;
    /** 商品详情页的拼团优惠信息；没有可用活动时为空 */
    private GroupBuyMarketEntity groupBuyMarket;

}
