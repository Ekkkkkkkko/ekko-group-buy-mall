package cn.ekko.trigger.http;

import cn.ekko.api.dto.CreatePayRequestDTO;
import cn.ekko.api.dto.QueryOrderListRequestDTO;
import cn.ekko.api.dto.QueryOrderListResponseDTO;
import cn.ekko.api.response.Response;
import cn.ekko.domain.auth.service.ILoginService;
import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.entity.ShopCartEntity;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import cn.ekko.domain.order.service.IOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AliPayControllerOrderTest {

    private AliPayController controller;
    private IOrderService orderService;
    private ILoginService loginService;

    @BeforeEach
    void setUp() {
        controller = new AliPayController();
        orderService = mock(IOrderService.class);
        loginService = mock(ILoginService.class);
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        ReflectionTestUtils.setField(controller, "loginService", loginService);
    }

    @Test
    void shouldRejectQueryForAnotherUser() {
        QueryOrderListRequestDTO request = new QueryOrderListRequestDTO();
        request.setUserId("other-user");
        request.setPageSize(10);
        when(loginService.resolveUserId("Bearer token")).thenReturn("login-user");

        Response<QueryOrderListResponseDTO> response = controller.queryUserOrderList(request, "Bearer token");

        assertEquals("1007", response.getCode());
        verify(orderService, never()).queryUserOrderList("other-user", null, 11);
    }

    @Test
    void shouldUseExtraRowToBuildCursorPage() {
        QueryOrderListRequestDTO request = new QueryOrderListRequestDTO();
        request.setUserId("1001");
        request.setPageSize(2);
        when(loginService.resolveUserId("Bearer token")).thenReturn("1001");
        when(orderService.queryUserOrderList("1001", null, 3))
                .thenReturn(List.of(order(30L), order(20L), order(10L)));

        Response<QueryOrderListResponseDTO> response = controller.queryUserOrderList(request, "Bearer token");

        assertEquals("0000", response.getCode());
        assertTrue(response.getData().isHasMore());
        assertEquals(2, response.getData().getOrderList().size());
        assertEquals(20L, response.getData().getLastId());
        assertFalse(response.getData().getOrderList().stream().anyMatch(item -> item.getId().equals(10L)));
    }

    @Test
    void shouldRejectCreateOrderForAnotherUser() throws Exception {
        CreatePayRequestDTO request = createPayRequest("other-user");
        when(loginService.resolveUserId("Bearer token")).thenReturn("login-user");

        Response<String> response = controller.createPayOrder(request, "Bearer token");

        assertEquals("1007", response.getCode());
        verify(orderService, never()).createOrder(any());
    }

    @Test
    void shouldCreateOrderWithAuthenticatedUser() throws Exception {
        CreatePayRequestDTO request = createPayRequest("login-user");
        when(loginService.resolveUserId("Bearer token")).thenReturn("login-user");
        when(orderService.createOrder(any())).thenReturn(PayOrderEntity.builder()
                .userId("login-user")
                .orderId("order-1")
                .payUrl("https://pay.example/order-1")
                .build());

        Response<String> response = controller.createPayOrder(request, "Bearer token");

        ArgumentCaptor<ShopCartEntity> cartCaptor = ArgumentCaptor.forClass(ShopCartEntity.class);
        verify(orderService).createOrder(cartCaptor.capture());
        assertEquals("0000", response.getCode());
        assertEquals("login-user", cartCaptor.getValue().getUserId());
    }

    @Test
    void shouldParseAlipayPaymentTimeAsShanghaiTimeWhenJvmUsesUtc() throws Exception {
        TimeZone previousTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            Date parsed = controller.parsePayTime("2026-09-03 11:27:02");

            assertEquals(Instant.parse("2026-09-03T03:27:02Z"), parsed.toInstant());
        } finally {
            TimeZone.setDefault(previousTimeZone);
        }
    }

    private CreatePayRequestDTO createPayRequest(String userId) {
        CreatePayRequestDTO request = new CreatePayRequestDTO();
        request.setUserId(userId);
        request.setProductId("product-1");
        request.setMarketType(0);
        return request;
    }

    private PayOrderEntity order(long id) {
        return PayOrderEntity.builder()
                .id(id)
                .userId("1001")
                .productId("product-1")
                .productName("测试商品")
                .orderId("order-" + id)
                .orderStatus(OrderStatusVO.PAY_WAIT)
                .marketType(MarketTypeVO.NO_MARKET)
                .build();
    }
}
