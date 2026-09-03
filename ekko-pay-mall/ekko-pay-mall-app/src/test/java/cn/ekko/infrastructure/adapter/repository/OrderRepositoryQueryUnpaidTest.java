package cn.ekko.infrastructure.adapter.repository;

import cn.ekko.domain.order.model.entity.ShopCartEntity;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import cn.ekko.infrastructure.dao.IOrderDao;
import cn.ekko.infrastructure.dao.po.PayOrder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderRepositoryQueryUnpaidTest {

    @Test
    void shouldPassMarketContextWhenQueryingReusableOrder() {
        IOrderDao orderDao = mock(IOrderDao.class);
        OrderRepository repository = new OrderRepository();
        ReflectionTestUtils.setField(repository, "orderDao", orderDao);

        repository.queryUnPayOrder(ShopCartEntity.builder()
                .userId("user-1")
                .productId("product-1")
                .marketType(MarketTypeVO.GROUP_BUY_MARKET)
                .activityId(2026072903L)
                .teamId("team-1")
                .build());

        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(orderDao).queryUnPayOrder(captor.capture());
        assertEquals(1, captor.getValue().getMarketType());
        assertEquals(2026072903L, captor.getValue().getActivityId());
        assertEquals("team-1", captor.getValue().getTeamId());
    }
}
