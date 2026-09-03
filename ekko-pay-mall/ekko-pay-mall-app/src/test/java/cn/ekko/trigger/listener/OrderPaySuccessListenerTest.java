package cn.ekko.trigger.listener;

import cn.ekko.domain.goods.service.IGoodsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderPaySuccessListenerTest {

    private IGoodsService goodsService;
    private OrderPaySuccessListener listener;

    @BeforeEach
    void setUp() {
        goodsService = mock(IGoodsService.class);
        listener = new OrderPaySuccessListener();
        ReflectionTestUtils.setField(listener, "goodsService", goodsService);
    }

    @Test
    void shouldFulfillMarketSettlementEvent() {
        listener.handleEvent("{\"userId\":\"1001\",\"tradeNo\":\"order-1\",\"marketSettlement\":true}");

        verify(goodsService).deliverGoods("1001", "order-1");
    }

    @Test
    void shouldKeepNormalPayEventAtLegacyEntry() {
        listener.handleEvent("{\"userId\":\"1002\",\"tradeNo\":\"order-2\",\"marketSettlement\":false}");

        verify(goodsService, never()).deliverGoods("1002", "order-2");
    }

}
