package cn.ekko.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundOrderResponseDTO {

    private String userId;
    private String orderId;
    private String status;
    /** 是否还需要第5步调用支付宝退款。 */
    private boolean refundRequired;
    private String info;
}
