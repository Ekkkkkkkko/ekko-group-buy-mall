package cn.ekko.infrastructure.adapter.port;

import cn.ekko.domain.order.model.entity.GroupBuyMarketEntity;
import cn.ekko.domain.product.adapter.port.IGroupBuyMarketQueryPort;
import cn.ekko.infrastructure.gateway.IGroupBuyMarketService;
import cn.ekko.infrastructure.gateway.dto.GroupBuyMarketResponse;
import cn.ekko.infrastructure.gateway.dto.QueryGroupBuyMarketRequestDTO;
import cn.ekko.infrastructure.gateway.dto.QueryGroupBuyMarketResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Call;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class GroupBuyMarketQueryPort implements IGroupBuyMarketQueryPort {

    private static final String SUCCESS_CODE = "0000";

    private final IGroupBuyMarketService groupBuyMarketService;
    private final String source;
    private final String channel;

    public GroupBuyMarketQueryPort(
            IGroupBuyMarketService groupBuyMarketService,
            @Value("${group-buy-market.source}") String source,
            @Value("${group-buy-market.channel}") String channel) {
        this.groupBuyMarketService = groupBuyMarketService;
        this.source = source;
        this.channel = channel;
    }

    @Override
    public GroupBuyMarketEntity queryGroupBuyMarket(String userId, String productId) {
        QueryGroupBuyMarketRequestDTO request = QueryGroupBuyMarketRequestDTO.builder()
                .userId(userId)
                .source(source)
                .channel(channel)
                .goodsId(productId)
                .build();
        try {
            Call<GroupBuyMarketResponse<QueryGroupBuyMarketResponseDTO>> call =
                    groupBuyMarketService.queryGroupBuyMarketConfig(request);
            retrofit2.Response<GroupBuyMarketResponse<QueryGroupBuyMarketResponseDTO>> httpResponse = call.execute();
            if (!httpResponse.isSuccessful()) {
                log.warn("商品详情查询拼团优惠HTTP失败 productId:{} status:{}", productId, httpResponse.code());
                return null;
            }

            GroupBuyMarketResponse<QueryGroupBuyMarketResponseDTO> responseBody = httpResponse.body();
            if (null == responseBody || !SUCCESS_CODE.equals(responseBody.getCode())) {
                log.info("商品暂无可展示的拼团优惠 productId:{} code:{} info:{}",
                        productId,
                        null == responseBody ? null : responseBody.getCode(),
                        null == responseBody ? null : responseBody.getInfo());
                return null;
            }

            QueryGroupBuyMarketResponseDTO data = responseBody.getData();
            if (null == data || null == data.getGoods()
                    || !productId.equals(data.getGoods().getGoodsId())
                    || null == data.getGoods().getOriginalPrice()
                    || null == data.getGoods().getDeductionPrice()
                    || null == data.getGoods().getPayPrice()) {
                log.warn("商品拼团优惠返回数据不完整 productId:{}", productId);
                return null;
            }
            return toEntity(data);
        } catch (IOException | RuntimeException e) {
            log.warn("商品详情查询拼团优惠失败，降级为普通商品 productId:{}", productId, e);
            return null;
        }
    }

    private GroupBuyMarketEntity toEntity(QueryGroupBuyMarketResponseDTO data) {
        List<GroupBuyMarketEntity.TeamEntity> teams = null == data.getTeamList()
                ? List.of()
                : data.getTeamList().stream().map(this::toTeamEntity).toList();
        return GroupBuyMarketEntity.builder()
                .activityId(data.getActivityId())
                .originalPrice(data.getGoods().getOriginalPrice())
                .deductionPrice(data.getGoods().getDeductionPrice())
                .payPrice(data.getGoods().getPayPrice())
                .teamList(teams)
                .teamStatistic(toTeamStatisticEntity(data.getTeamStatistic()))
                .build();
    }

    private GroupBuyMarketEntity.TeamEntity toTeamEntity(QueryGroupBuyMarketResponseDTO.TeamDTO team) {
        return GroupBuyMarketEntity.TeamEntity.builder()
                .userId(team.getUserId())
                .teamId(team.getTeamId())
                .activityId(team.getActivityId())
                .targetCount(team.getTargetCount())
                .completeCount(team.getCompleteCount())
                .lockCount(team.getLockCount())
                .validStartTime(team.getValidStartTime())
                .validEndTime(team.getValidEndTime())
                .validTimeCountdown(team.getValidTimeCountdown())
                .outTradeNo(team.getOutTradeNo())
                .build();
    }

    private GroupBuyMarketEntity.TeamStatisticEntity toTeamStatisticEntity(
            QueryGroupBuyMarketResponseDTO.TeamStatisticDTO statistic) {
        if (null == statistic) return null;
        return GroupBuyMarketEntity.TeamStatisticEntity.builder()
                .allTeamCount(statistic.getAllTeamCount())
                .allTeamCompleteCount(statistic.getAllTeamCompleteCount())
                .allTeamUserCount(statistic.getAllTeamUserCount())
                .build();
    }
}
