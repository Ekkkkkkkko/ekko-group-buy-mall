package cn.ekko.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundMarketPayOrderResponseDTO {

    private String userId;
    private String orderId;
    private String teamId;
    private String code;
    private String info;
}
