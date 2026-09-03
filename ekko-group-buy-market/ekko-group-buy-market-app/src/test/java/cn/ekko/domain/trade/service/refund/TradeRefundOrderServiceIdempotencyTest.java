package cn.ekko.domain.trade.service.refund;

import cn.ekko.domain.trade.adapter.repository.ITradeRepository;
import cn.ekko.domain.trade.model.entity.MarketPayOrderEntity;
import cn.ekko.domain.trade.model.entity.TradeRefundBehaviorEntity;
import cn.ekko.domain.trade.model.entity.TradeRefundCommandEntity;
import cn.ekko.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.ekko.domain.trade.service.refund.factory.TradeRefundRuleFilterFactory;
import cn.ekko.types.design.framework.link.model2.chain.BusinessLinkedList;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeRefundOrderServiceIdempotencyTest {

    @Test
    void shouldReturnRepeatAfterConcurrentConditionalUpdateLosesRace() throws Exception {
        ITradeRepository repository = mock(ITradeRepository.class);
        TradeRefundOrderService service = new TradeRefundOrderService(repository, Map.of());
        BusinessLinkedList<TradeRefundCommandEntity, TradeRefundRuleFilterFactory.DynamicContext, TradeRefundBehaviorEntity>
                chain = new BusinessLinkedList<>("test-refund-chain");
        chain.add((request, context) -> {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        });
        ReflectionTestUtils.setField(service, "tradeRefundRuleFilter", chain);

        TradeRefundCommandEntity command = TradeRefundCommandEntity.builder()
                .userId("1001")
                .outTradeNo("mall-order-1")
                .source("s01")
                .channel("c01")
                .build();
        when(repository.queryMarketPayOrderEntityByOutTradeNo("1001", "mall-order-1", "s01", "c01"))
                .thenReturn(MarketPayOrderEntity.builder()
                        .orderId("market-order-1")
                        .teamId("team-1")
                        .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.CLOSE)
                        .build());

        TradeRefundBehaviorEntity result = service.refundOrder(command);

        assertEquals(TradeRefundBehaviorEntity.TradeRefundBehaviorEnum.REPEAT,
                result.getTradeRefundBehaviorEnum());
        assertEquals("market-order-1", result.getOrderId());
    }
}
