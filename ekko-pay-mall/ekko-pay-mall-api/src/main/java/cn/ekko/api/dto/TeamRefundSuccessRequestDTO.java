package cn.ekko.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 拼团退单完成消息。outTradeNo 是商城订单号，orderId 是拼团内部订单号。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeamRefundSuccessRequestDTO {

    private String type;
    private String userId;
    private String teamId;
    private String orderId;
    private String outTradeNo;
    private Long activityId;
}
