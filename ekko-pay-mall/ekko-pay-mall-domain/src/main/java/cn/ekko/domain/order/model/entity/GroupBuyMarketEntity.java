package cn.ekko.domain.order.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 商品详情页使用的拼团营销信息。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupBuyMarketEntity {

    private Long activityId;
    private BigDecimal originalPrice;
    private BigDecimal deductionPrice;
    private BigDecimal payPrice;
    private List<TeamEntity> teamList;
    private TeamStatisticEntity teamStatistic;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TeamEntity {
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
    public static class TeamStatisticEntity {
        private Integer allTeamCount;
        private Integer allTeamCompleteCount;
        private Integer allTeamUserCount;
    }
}
