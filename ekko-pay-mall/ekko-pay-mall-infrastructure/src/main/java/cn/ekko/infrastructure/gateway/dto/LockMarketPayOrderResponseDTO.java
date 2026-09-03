package cn.ekko.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockMarketPayOrderResponseDTO {

    /**
     * 拼团系统内部营销订单号
     */
    private String orderId;

    /**
     * 拼团队伍ID
     */
    private String teamId;

    /**
     * 商品原价
     */
    private BigDecimal originalPrice;

    /**
     * 营销优惠金额
     */
    private BigDecimal deductionPrice;

    /**
     * 最终实际支付金额
     */
    private BigDecimal payPrice;

    /**
     * 拼团交易订单状态
     */
    private Integer tradeOrderStatus;
}