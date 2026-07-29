package cn.ekko.domain.auth.service;

import cn.ekko.domain.auth.adapter.port.ILoginPort;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.google.common.cache.Cache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * @author Ekko
 * @description 微信服务
 */
@Service
public class WeixinLoginService implements ILoginService {

    @Resource
    private ILoginPort loginPort;
    @Resource
    private Cache<String, String> openidToken;
    @Value("${auth.jwt-secret:}")
    private String jwtSecret;

    @Override
    public String createQrCodeTicket() {
        try{
            return loginPort.createQrCodeTicket();
        } catch (Exception e){
            throw new AppException(e.getMessage());
        }
    }

    @Override
    public String checkLogin(String ticket) {
        // 通过 ticket 判断用户是否完成扫码；完成后签发短期登录令牌，不直接暴露 openid 作为凭证。
        String openid = openidToken.getIfPresent(ticket);
        if (null == openid) return null;
        if (null == jwtSecret || jwtSecret.isBlank()) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "AUTH_JWT_SECRET未配置");
        }
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(openid)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .sign(Algorithm.HMAC256(jwtSecret));
    }

    @Override
    public String resolveUserId(String authorization) {
        if (null == authorization || !authorization.startsWith("Bearer ")) {
            throw new AppException(ResponseCode.AUTH_REQUIRED.getCode(), ResponseCode.AUTH_REQUIRED.getInfo());
        }
        if (null == jwtSecret || jwtSecret.isBlank()) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "AUTH_JWT_SECRET未配置");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new AppException(ResponseCode.AUTH_REQUIRED.getCode(), ResponseCode.AUTH_REQUIRED.getInfo());
        }
        try {
            String userId = JWT.require(Algorithm.HMAC256(jwtSecret))
                    .build()
                    .verify(token)
                    .getSubject();
            if (null == userId || userId.isBlank()) {
                throw new AppException(ResponseCode.AUTH_REQUIRED.getCode(), ResponseCode.AUTH_REQUIRED.getInfo());
            }
            return userId;
        } catch (JWTVerificationException e) {
            throw new AppException(ResponseCode.AUTH_REQUIRED.getCode(), "登录令牌无效或已过期", e);
        }
    }

    @Override
    public void saveLoginState(String ticket, String openid) throws IOException {
        // 实际的业务场景，openid 可以生成 jwt 的 token 让前端存储
        openidToken.put(ticket, openid);
        // 发送登录成功模板消息
        loginPort.sendLoginTempleteMessage(openid);
    }

}
