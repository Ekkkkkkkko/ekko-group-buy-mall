package cn.ekko.domain.order.service;

import cn.ekko.domain.order.adapter.port.IGroupBuyMarketPort;
import cn.ekko.domain.order.adapter.port.IProductPort;
import cn.ekko.domain.order.adapter.repository.IOrderRepository;
import cn.ekko.domain.order.model.entity.OrderEntity;
import cn.ekko.domain.order.model.entity.ProductEntity;
import cn.ekko.domain.order.model.entity.ShopCartEntity;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import com.alipay.api.AlipayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceCreateOrderTest {

    private IOrderRepository repository;
    private IProductPort productPort;
    private IGroupBuyMarketPort groupBuyMarketPort;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        repository = mock(IOrderRepository.class);
        productPort = mock(IProductPort.class);
        groupBuyMarketPort = mock(IGroupBuyMarketPort.class);
        orderService = new OrderService(repository, productPort, groupBuyMarketPort);
        ReflectionTestUtils.setField(orderService, "alipayClient", mock(AlipayClient.class));
    }

    @Test
    void shouldRejectBeforeCreatingOrLockingOrderWhenPaymentIsNotConfigured() {
        ReflectionTestUtils.setField(orderService, "alipayClient", null);

        AppException exception = assertThrows(AppException.class,
                () -> orderService.createOrder(groupBuyCart()));

        assertEquals("支付功能尚未配置", exception.getInfo());
        verifyNoInteractions(repository, productPort, groupBuyMarketPort);
    }

    @Test
    void shouldCloseLocalCreateOrderWhenGroupBuyBusinessRejectsLock() {
        when(repository.queryUnPayOrder(any())).thenReturn(null);
        when(productPort.queryProductByProductId("product-1")).thenReturn(product());
        when(groupBuyMarketPort.lockMarketPayOrder(any(), any(), any(), any(), any()))
                .thenThrow(new AppException(
                        ResponseCode.GROUP_BUY_BUSINESS_ERROR.getCode(),
                        "远端业务拒绝"
                ));
        when(repository.refundOrder(any(), any(), any())).thenReturn(1);

        assertThrows(AppException.class, () -> orderService.createOrder(groupBuyCart()));

        ArgumentCaptor<String> orderIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).refundOrder(eq("user-1"), orderIdCaptor.capture(), eq(OrderStatusVO.CREATE));
        org.junit.jupiter.api.Assertions.assertEquals(16, orderIdCaptor.getValue().length());
    }

    @Test
    void shouldKeepLocalCreateOrderWhenGroupBuyResultIsUncertain() {
        when(repository.queryUnPayOrder(any())).thenReturn(null);
        when(productPort.queryProductByProductId("product-1")).thenReturn(product());
        when(groupBuyMarketPort.lockMarketPayOrder(any(), any(), any(), any(), any()))
                .thenThrow(new AppException(
                        ResponseCode.GROUP_BUY_HTTP_ERROR.getCode(),
                        "网络异常"
                ));

        assertThrows(AppException.class, () -> orderService.createOrder(groupBuyCart()));

        verify(repository, never()).refundOrder(any(), any(), any());
    }

    private ShopCartEntity groupBuyCart() {
        return ShopCartEntity.builder()
                .userId("user-1")
                .productId("product-1")
                .marketType(MarketTypeVO.GROUP_BUY_MARKET)
                .activityId(2026072903L)
                .build();
    }

    private ProductEntity product() {
        return ProductEntity.builder()
                .productId("product-1")
                .productName("测试商品")
                .price(new BigDecimal("100.00"))
                .build();
    }
}
