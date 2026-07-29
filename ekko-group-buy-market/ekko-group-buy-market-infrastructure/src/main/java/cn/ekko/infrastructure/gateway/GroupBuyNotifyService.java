package cn.ekko.infrastructure.gateway;

import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * @author Ekko
 * @description 拼团回调服务
 */
@Slf4j
@Service
public class GroupBuyNotifyService {

    @Resource
    private OkHttpClient okHttpClient;
    @Value("${group-buy-market.notify-token:}")
    private String notifyToken;

    public String groupBuyNotify(String apiUrl, String notifyRequestDTOJSON) throws Exception {
        try {
            if (null == notifyToken || notifyToken.isBlank()) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "拼团成团通知令牌未配置");
            }
            // 1. 构建参数
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, notifyRequestDTOJSON);
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .addHeader("content-type", "application/json")
                    .addHeader("X-Group-Buy-Token", notifyToken)
                    .build();

            // 2. 调用接口
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || null == response.body()) {
                    log.warn("拼团回调 HTTP 返回异常 apiUrl:{} httpCode:{}", apiUrl, response.code());
                    return "error";
                }

                // 3. 返回结果
                return response.body().string().trim();
            }
        } catch (Exception e) {
            log.error("拼团回调 HTTP 接口服务异常 {}", apiUrl, e);
            throw new AppException(ResponseCode.HTTP_EXCEPTION);
        }
    }

}
