package cn.ekko.domain.trade.service.settlement.factory;

import cn.ekko.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.ekko.domain.trade.model.entity.MarketPayOrderEntity;
import cn.ekko.domain.trade.model.entity.TradeSettlementRuleCommandEntity;
import cn.ekko.domain.trade.model.entity.TradeSettlementRuleFilterBackEntity;
import cn.ekko.domain.trade.service.settlement.filter.EndRuleFilter;
import cn.ekko.domain.trade.service.settlement.filter.OutTradeNoRuleFilter;
import cn.ekko.domain.trade.service.settlement.filter.SCRuleFilter;
import cn.ekko.domain.trade.service.settlement.filter.SettableRuleFilter;
import cn.ekko.types.design.framework.link.model2.LinkArmory;
import cn.ekko.types.design.framework.link.model2.chain.BusinessLinkedList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

/**
 * @author Ekko
 * @description 交易结算规则过滤工厂
 */
@Slf4j
@Service
public class TradeSettlementRuleFilterFactory {

    @Bean("tradeSettlementRuleFilter")
    public BusinessLinkedList<TradeSettlementRuleCommandEntity,
            DynamicContext, TradeSettlementRuleFilterBackEntity> tradeSettlementRuleFilter(
            SCRuleFilter scRuleFilter,
            OutTradeNoRuleFilter outTradeNoRuleFilter,
            SettableRuleFilter settableRuleFilter,
            EndRuleFilter endRuleFilter) {

        // 组装链
        LinkArmory<TradeSettlementRuleCommandEntity, DynamicContext, TradeSettlementRuleFilterBackEntity> linkArmory =
                new LinkArmory<>("交易结算规则过滤链", outTradeNoRuleFilter, scRuleFilter, settableRuleFilter, endRuleFilter);

        // 链对象
        return linkArmory.getLogicLink();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {
        // 订单营销实体对象
        private MarketPayOrderEntity marketPayOrderEntity;
        // 拼团组队实体对象
        private GroupBuyTeamEntity groupBuyTeamEntity;
    }

}
