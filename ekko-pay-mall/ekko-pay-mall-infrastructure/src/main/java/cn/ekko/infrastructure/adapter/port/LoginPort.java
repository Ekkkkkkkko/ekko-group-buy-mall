package cn.ekko.infrastructure.adapter.port;

import cn.ekko.domain.auth.adapter.port.ILoginPort;
import cn.ekko.infrastructure.gateway.IWeixinApiService;
import cn.ekko.infrastructure.gateway.dto.WeixinQrCodeRequestDTO;
import cn.ekko.infrastructure.gateway.dto.WeixinQrCodeResponseDTO;
import cn.ekko.infrastructure.gateway.dto.WeixinTemplateMessageDTO;
import cn.ekko.infrastructure.gateway.dto.WeixinTokenResponseDTO;
import com.google.common.cache.Cache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Call;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class LoginPort implements ILoginPort {

    private static final int QR_CODE_EXPIRE_SECONDS = 300;

    @Value("${weixin.config.app-id}")
    private String appid;
    @Value("${weixin.config.app-secret}")
    private String appSecret;
    @Value("${weixin.config.template_id}")
    private String template_id;
    @Resource(name = "weixinAccessToken")
    private Cache<String, String> weixinAccessToken;
    @Resource
    private IWeixinApiService weixinApiService;

    @Override
    public String createQrCodeTicket() throws IOException {
        String sceneStr = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);
        return createQrCodeTicket(sceneStr);
    }

    @Override
    public String createQrCodeTicket(String sceneStr) throws IOException {
        // 1. 获取 accessToken
        String accessToken = getAccessToken();

        // 2. 生成 ticket
        WeixinQrCodeRequestDTO request = WeixinQrCodeRequestDTO.builder()
                .expire_seconds(QR_CODE_EXPIRE_SECONDS)
                .action_name(WeixinQrCodeRequestDTO.ActionNameTypeVO.QR_STR_SCENE.getCode())
                .action_info(WeixinQrCodeRequestDTO.ActionInfo.builder()
                        .scene(WeixinQrCodeRequestDTO.ActionInfo.Scene.builder()
                                .scene_str(sceneStr)
                                .build())
                        .build())
                .build();

        Call<WeixinQrCodeResponseDTO> qrCodeCall = weixinApiService.createQrCode(accessToken, request);
        retrofit2.Response<WeixinQrCodeResponseDTO> response = qrCodeCall.execute();
        WeixinQrCodeResponseDTO weixinQrCodeResponseDTO = response.body();
        if (!response.isSuccessful()
                || null == weixinQrCodeResponseDTO
                || null == weixinQrCodeResponseDTO.getTicket()
                || weixinQrCodeResponseDTO.getTicket().isBlank()) {
            throw new IOException("生成微信二维码 ticket 失败");
        }
        return weixinQrCodeResponseDTO.getTicket();
    }

    @Override
    public void sendLoginTempleteMessage(String openid) throws IOException {
        // 1. 获取 accessToken
        String accessToken = getAccessToken();

        // 2. 发送模板消息
        Map<String, Map<String, String>> data = new HashMap<>();
        WeixinTemplateMessageDTO.put(data, WeixinTemplateMessageDTO.TemplateKey.USER, openid);

        WeixinTemplateMessageDTO templateMessageDTO = new WeixinTemplateMessageDTO(openid, template_id);
        templateMessageDTO.setUrl("https://gaga.plus");
        templateMessageDTO.setData(data);

        Call<Void> call = weixinApiService.sendMessage(accessToken, templateMessageDTO);
        retrofit2.Response<Void> response = call.execute();
        if (!response.isSuccessful()) {
            throw new IOException("发送微信登录成功模板消息失败");
        }
    }

    private String getAccessToken() throws IOException {
        String accessToken = weixinAccessToken.getIfPresent(appid);
        if (null != accessToken && !accessToken.isBlank()) {
            return accessToken;
        }

        Call<WeixinTokenResponseDTO> call = weixinApiService.getToken("client_credential", appid, appSecret);
        retrofit2.Response<WeixinTokenResponseDTO> response = call.execute();
        WeixinTokenResponseDTO responseBody = response.body();
        if (!response.isSuccessful()
                || null == responseBody
                || null == responseBody.getAccess_token()
                || responseBody.getAccess_token().isBlank()) {
            throw new IOException("获取微信 accessToken 失败");
        }
        accessToken = responseBody.getAccess_token();
        weixinAccessToken.put(appid, accessToken);
        return accessToken;
    }

}
