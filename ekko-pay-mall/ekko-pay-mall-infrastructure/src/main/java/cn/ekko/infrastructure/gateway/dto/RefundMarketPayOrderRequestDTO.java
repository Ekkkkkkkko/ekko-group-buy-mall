package cn.ekko.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundMarketPayOrderRequestDTO {

    private String userId;
    private String outTradeNo;
    private String source;
    private String channel;
}
