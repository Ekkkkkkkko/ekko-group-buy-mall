package cn.ekko.infrastructure.adapter.port;

import cn.ekko.infrastructure.gateway.IWeixinApiService;
import cn.ekko.infrastructure.gateway.dto.WeixinQrCodeRequestDTO;
import cn.ekko.infrastructure.gateway.dto.WeixinQrCodeResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit2.Call;
import retrofit2.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginPortTest {

    private LoginPort loginPort;
    private IWeixinApiService weixinApiService;
    private Cache<String, String> weixinAccessToken;

    @BeforeEach
    void setUp() {
        loginPort = new LoginPort();
        weixinApiService = mock(IWeixinApiService.class);
        weixinAccessToken = CacheBuilder.newBuilder().build();
        weixinAccessToken.put("app-id", "access-token");

        ReflectionTestUtils.setField(loginPort, "appid", "app-id");
        ReflectionTestUtils.setField(loginPort, "appSecret", "app-secret");
        ReflectionTestUtils.setField(loginPort, "weixinAccessToken", weixinAccessToken);
        ReflectionTestUtils.setField(loginPort, "weixinApiService", weixinApiService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendStringSceneToWeixin() throws Exception {
        Call<WeixinQrCodeResponseDTO> qrCodeCall = mock(Call.class);
        WeixinQrCodeResponseDTO responseDTO = new WeixinQrCodeResponseDTO();
        responseDTO.setTicket("ticket-1");
        when(weixinApiService.createQrCode(eq("access-token"), any())).thenReturn(qrCodeCall);
        when(qrCodeCall.execute()).thenReturn(Response.success(responseDTO));

        String ticket = loginPort.createQrCodeTicket("SCENE-01");

        ArgumentCaptor<WeixinQrCodeRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(WeixinQrCodeRequestDTO.class);
        verify(weixinApiService).createQrCode(eq("access-token"), requestCaptor.capture());
        WeixinQrCodeRequestDTO request = requestCaptor.getValue();
        assertEquals("ticket-1", ticket);
        assertEquals(300, request.getExpire_seconds());
        assertEquals("QR_STR_SCENE", request.getAction_name());
        assertEquals("SCENE-01", request.getAction_info().getScene().getScene_str());
        assertNull(request.getAction_info().getScene().getScene_id());
        assertFalse(new ObjectMapper().writeValueAsString(request).contains("scene_id"));
    }
}
