package cn.ekko.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QueryGroupBuyMarketResponseDTO {

    private Long activityId;
    private GoodsDTO goods;
    private List<TeamDTO> teamList;
    private TeamStatisticDTO teamStatistic;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GoodsDTO {
        private String goodsId;
        private BigDecimal originalPrice;
        private BigDecimal deductionPrice;
        private BigDecimal payPrice;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TeamDTO {
        private String userId;
        private String teamId;
        private Long activityId;
        private Integer targetCount;
        private Integer completeCount;
        private Integer lockCount;
        private Date validStartTime;
        private Date validEndTime;
        private String validTimeCountdown;
        private String outTradeNo;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TeamStatisticDTO {
        private Integer allTeamCount;
        private Integer allTeamCompleteCount;
        private Integer allTeamUserCount;
    }
}
