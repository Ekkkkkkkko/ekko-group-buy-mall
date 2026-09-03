package cn.ekko.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementMarketPayOrderRequestDTO {

    private String source;
    private String channel;
    private String userId;
    private String outTradeNo;
    private Date outTradeTime;
}
