package cn.ekko.domain.trade.service.refund;

import cn.ekko.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.ekko.domain.trade.adapter.repository.ITradeRepository;
import cn.ekko.domain.trade.model.entity.*;
import cn.ekko.domain.trade.model.valobj.RefundTypeEnumVO;
import cn.ekko.domain.trade.model.valobj.TeamRefundSuccess;
import cn.ekko.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.ekko.domain.trade.service.ITradeRefundOrderService;
import cn.ekko.domain.trade.service.refund.business.IRefundOrderStrategy;
import cn.ekko.domain.trade.service.refund.factory.TradeRefundRuleFilterFactory;
import cn.ekko.types.design.framework.link.model2.chain.BusinessLinkedList;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 退单，逆向流程服务
 *
 * @author Ekko
 */
@Slf4j
@Service
public class TradeRefundOrderService implements ITradeRefundOrderService {

    @Resource
    private BusinessLinkedList<TradeRefundCommandEntity, TradeRefundRuleFilterFactory.DynamicContext, TradeRefundBehaviorEntity> tradeRefundRuleFilter;

    private final ITradeRepository repository;

    private final Map<String, IRefundOrderStrategy> refundOrderStrategyMap;

    public TradeRefundOrderService(ITradeRepository repository, Map<String, IRefundOrderStrategy> refundOrderStrategyMap) {
        this.repository = repository;
        this.refundOrderStrategyMap = refundOrderStrategyMap;
    }

    @Override
    public TradeRefundBehaviorEntity refundOrder(TradeRefundCommandEntity tradeRefundCommandEntity) throws Exception {
        log.info("逆向流程，退单操作 userId:{} outTradeNo:{}", tradeRefundCommandEntity.getUserId(), tradeRefundCommandEntity.getOutTradeNo());
        try {
            return tradeRefundRuleFilter.apply(tradeRefundCommandEntity, new TradeRefundRuleFilterFactory.DynamicContext());
        } catch (AppException e) {
            if (!ResponseCode.UPDATE_ZERO.getCode().equals(e.getCode())) {
                throw e;
            }

            // 并发相同退单时，后到请求可能在条件更新处得到0行；回查已关闭明细后按幂等成功返回。
            MarketPayOrderEntity latestOrder = repository.queryMarketPayOrderEntityByOutTradeNo(
                    tradeRefundCommandEntity.getUserId(),
                    tradeRefundCommandEntity.getOutTradeNo(),
                    tradeRefundCommandEntity.getSource(),
                    tradeRefundCommandEntity.getChannel());
            if (null != latestOrder
                    && TradeOrderStatusEnumVO.CLOSE.equals(latestOrder.getTradeOrderStatusEnumVO())) {
                return TradeRefundBehaviorEntity.builder()
                        .userId(tradeRefundCommandEntity.getUserId())
                        .orderId(latestOrder.getOrderId())
                        .teamId(latestOrder.getTeamId())
                        .tradeRefundBehaviorEnum(TradeRefundBehaviorEntity.TradeRefundBehaviorEnum.REPEAT)
                        .build();
            }
            throw e;
        }
    }

    @Override
    public void restoreTeamLockStock(TeamRefundSuccess teamRefundSuccess) throws Exception {
        log.info("逆向流程，恢复锁单量 userId:{} activityId:{} teamId:{}", teamRefundSuccess.getUserId(), teamRefundSuccess.getActivityId(), teamRefundSuccess.getTeamId());
        String type = teamRefundSuccess.getType();

        // 根据枚举值获取对应的退单类型
        RefundTypeEnumVO refundTypeEnumVO = RefundTypeEnumVO.getRefundTypeEnumVOByCode(type);
        IRefundOrderStrategy refundOrderStrategy = refundOrderStrategyMap.get(refundTypeEnumVO.getStrategy());

        // 逆向库存操作，恢复锁单量
        refundOrderStrategy.reverseStock(teamRefundSuccess);
    }

    @Override
    public List<UserGroupBuyOrderDetailEntity> queryTimeoutUnpaidOrderList() {
        log.info("扫描数据，超时组队未支付订单");
        return repository.queryTimeoutUnpaidOrderList();
    }

}
