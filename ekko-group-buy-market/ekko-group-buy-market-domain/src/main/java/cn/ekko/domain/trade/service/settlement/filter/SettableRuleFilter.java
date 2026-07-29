package cn.ekko.domain.trade.service.settlement.filter;

import cn.ekko.domain.trade.adapter.repository.ITradeRepository;
import cn.ekko.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.ekko.domain.trade.model.entity.MarketPayOrderEntity;
import cn.ekko.domain.trade.model.entity.TradeSettlementRuleCommandEntity;
import cn.ekko.domain.trade.model.entity.TradeSettlementRuleFilterBackEntity;
import cn.ekko.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.ekko.domain.trade.service.settlement.factory.TradeSettlementRuleFilterFactory;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import cn.ekko.types.design.framework.link.model2.handler.ILogicHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;

/**
 * @author Ekko
 * @description 可结算规则过滤；交易时间
 */
@Slf4j
@Service
public class SettableRuleFilter implements ILogicHandler<TradeSettlementRuleCommandEntity, TradeSettlementRuleFilterFactory.DynamicContext, TradeSettlementRuleFilterBackEntity> {

    @Resource
    private ITradeRepository repository;

    @Override
    public TradeSettlementRuleFilterBackEntity apply(TradeSettlementRuleCommandEntity requestParameter, TradeSettlementRuleFilterFactory.DynamicContext dynamicContext) throws Exception {
        log.info("结算规则过滤-有效时间校验{} outTradeNo:{}", requestParameter.getUserId(), requestParameter.getOutTradeNo());

        // 上下文；获取数据
        MarketPayOrderEntity marketPayOrderEntity = dynamicContext.getMarketPayOrderEntity();

        // 查询拼团对象
        GroupBuyTeamEntity groupBuyTeamEntity = repository.queryGroupBuyTeamByTeamId(marketPayOrderEntity.getTeamId());

        // 外部交易时间 - 也就是用户支付完成的时间，这个时间要在拼团有效时间范围内
        Date outTradeTime = requestParameter.getOutTradeTime();

        // 首次结算必须在拼团有效期内。已结算明细的重复请求直接进入幂等回查，
        // 不能因为重试发生在队伍过期后而将原本成功的结算改判为失败。
        if (!TradeOrderStatusEnumVO.COMPLETE.equals(marketPayOrderEntity.getTradeOrderStatusEnumVO())
                && !outTradeTime.before(groupBuyTeamEntity.getValidEndTime())) {
            log.error("订单交易时间不在拼团有效时间范围内");
            throw new AppException(ResponseCode.E0106);
        }

        // 设置上下文
        dynamicContext.setGroupBuyTeamEntity(groupBuyTeamEntity);

        return next(requestParameter, dynamicContext);
    }

}
