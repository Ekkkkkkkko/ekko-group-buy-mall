package cn.ekko.domain.trade.model.aggregate;

import cn.ekko.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.ekko.domain.trade.model.entity.TradePaySuccessEntity;
import cn.ekko.domain.trade.model.entity.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Ekko
 * @description 拼团组队结算聚合
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupBuyTeamSettlementAggregate {

    /** 用户实体对象 */
    private UserEntity userEntity;
    /** 拼团组队实体对象 */
    private GroupBuyTeamEntity groupBuyTeamEntity;
    /** 交易支付订单实体对象 */
    private TradePaySuccessEntity tradePaySuccessEntity;

}
