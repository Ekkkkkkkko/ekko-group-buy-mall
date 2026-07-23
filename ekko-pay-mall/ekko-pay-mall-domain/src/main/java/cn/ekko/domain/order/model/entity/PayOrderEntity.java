package cn.ekko.domain.order.model.entity;

import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付单实体对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayOrderEntity {

    /** 用户ID */
    private String userId;
    /** 订单ID */
    private String orderId;
    /** 支付地址；创建支付后，获得支付信息；*/
    private String payUrl;
    /** 订单状态；0-创建完成、1-等待支付、2-支付成功、3-交易完成、4-订单关单 */
    private OrderStatusVO orderStatus;

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
