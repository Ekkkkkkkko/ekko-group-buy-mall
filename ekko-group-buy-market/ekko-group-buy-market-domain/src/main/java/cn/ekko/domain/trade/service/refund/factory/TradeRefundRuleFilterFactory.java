package cn.ekko.domain.trade.service.refund.factory;

import cn.ekko.domain.trade.model.entity.*;
import cn.ekko.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.ekko.domain.trade.service.lock.filter.ActivityUsabilityRuleFilter;
import cn.ekko.domain.trade.service.lock.filter.TeamStockOccupyRuleFilter;
import cn.ekko.domain.trade.service.lock.filter.UserTakeLimitRuleFilter;
import cn.ekko.domain.trade.service.refund.filter.DataNodeFilter;
import cn.ekko.domain.trade.service.refund.filter.RefundOrderNodeFilter;
import cn.ekko.domain.trade.service.refund.filter.UniqueRefundNodeFilter;
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
 * 交易退单工程
 *
 * @author Ekko
 */
@Slf4j
@Service
public class TradeRefundRuleFilterFactory {

    @Bean("tradeRefundRuleFilter")
    public BusinessLinkedList<TradeRefundCommandEntity, TradeRefundRuleFilterFactory.DynamicContext, TradeRefundBehaviorEntity> tradeRefundRuleFilter(
            DataNodeFilter dataNodeFilter,
            UniqueRefundNodeFilter uniqueRefundNodeFilter,
            RefundOrderNodeFilter refundOrderNodeFilter) {

        // 组装链
        LinkArmory<TradeRefundCommandEntity, TradeRefundRuleFilterFactory.DynamicContext, TradeRefundBehaviorEntity> linkArmory =
                new LinkArmory<>("退单规则过滤链",
                        dataNodeFilter,
                        uniqueRefundNodeFilter,
                        refundOrderNodeFilter);

        // 链对象
        return linkArmory.getLogicLink();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {

        private MarketPayOrderEntity marketPayOrderEntity;

        private GroupBuyTeamEntity groupBuyTeamEntity;

    }

}
