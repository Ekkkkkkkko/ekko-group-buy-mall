package cn.ekko.domain.order.model.entity;

import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 支付单实体对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayOrderEntity {

    /** 自增ID，用于订单列表游标。 */
    private Long id;
    /** 用户ID */
    private String userId;
    private String productId;
    private String productName;
    /** 订单ID */
    private String orderId;
    private Date orderTime;
    private BigDecimal totalAmount;
    /** 支付地址；创建支付后，获得支付信息；*/
    private String payUrl;
    /** 订单状态；0-创建完成、1-等待支付、2-支付成功、3-交易完成、4-订单关单 */
    private OrderStatusVO orderStatus;
    private MarketTypeVO marketType;
    private Long activityId;
    private String teamId;
    private BigDecimal marketDeductionAmount;
    private BigDecimal payAmount;
    /** 支付宝确认的支付成功时间 */
    private Date payTime;

    @Override
    public String toString() {
        return "PayOrderEntity{" +
                "userId='" + userId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", payUrl='" + payUrl + '\'' +
                ", orderStatus=" + orderStatus +
                '}';
    }

}
