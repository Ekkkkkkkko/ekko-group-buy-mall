package cn.ekko.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QueryGroupBuyMarketRequestDTO {

    private String userId;
    private String source;
    private String channel;
    private String goodsId;
}
