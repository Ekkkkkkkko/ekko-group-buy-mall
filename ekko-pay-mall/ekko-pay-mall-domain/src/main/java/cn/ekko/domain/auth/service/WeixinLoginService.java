package cn.ekko.domain.auth.service;

import cn.ekko.domain.auth.adapter.port.ILoginPort;
import cn.ekko.domain.auth.adapter.repository.IUserAccountRepository;
import cn.ekko.domain.auth.model.entity.UserAccountEntity;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.google.common.cache.Cache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author Ekko
 * @description 商城账号、微信扫码与 JWT 登录服务
 */
@Service
public class WeixinLoginService implements ILoginService {

    private static final Pattern SCENE_STR_PATTERN = Pattern.compile("[A-Z0-9_-]{1,64}");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9_]{4,32}");
    private static final Pattern PASSWORD_LETTER_PATTERN = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern PASSWORD_NUMBER_PATTERN = Pattern.compile(".*[0-9].*");
    private static final String ACCOUNT_AUTH_TYPE = "account";
    private static final String WEIXIN_AUTH_TYPE = "weixin";
    private static final String AUTH_TYPE_CLAIM = "authType";
    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";

    @Resource
    private ILoginPort loginPort;
    @Resource(name = "openidToken")
    private Cache<String, String> openidToken;
    @Resource(name = "sceneTicket")
    private Cache<String, String> sceneTicket;
    @Resource(name = "ticketScene")
    private Cache<String, String> ticketScene;
    @Resource
    private IUserAccountRepository userAccountRepository;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Value("${auth.jwt-secret:}")
    private String jwtSecret;

    @Override
    public String register(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        validateNewPassword(password);
        if (null != userAccountRepository.queryByUsername(normalizedUsername)) {
            throw new AppException(ResponseCode.ACCOUNT_EXISTS.getCode(), ResponseCode.ACCOUNT_EXISTS.getInfo());
        }

        UserAccountEntity account = UserAccountEntity.builder()
                .userId("USR_" + UUID.randomUUID().toString().replace("-", ""))
                .username(normalizedUsername)
                .passwordHash(passwordEncoder.encode(password))
                .status(UserAccountEntity.ENABLED)
                .tokenVersion(0)
                .build();
        if (!userAccountRepository.create(account)) {
            throw new AppException(ResponseCode.ACCOUNT_EXISTS.getCode(), ResponseCode.ACCOUNT_EXISTS.getInfo());
        }
        return issueJwt(account.getUserId(), ACCOUNT_AUTH_TYPE, account.getTokenVersion());
    }

    @Override
    public String login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        if (null == password || password.isBlank()) {
            throw credentialError();
        }
        UserAccountEntity account = userAccountRepository.queryByUsername(normalizedUsername);
        if (null == account || !passwordEncoder.matches(password, account.getPasswordHash())) {
            throw credentialError();
        }
        if (!account.isEnabled()) {
            throw new AppException(ResponseCode.ACCOUNT_DISABLED.getCode(), ResponseCode.ACCOUNT_DISABLED.getInfo());
        }
        return issueJwt(account.getUserId(), ACCOUNT_AUTH_TYPE, account.getTokenVersion());
    }

    @Override
    public void logout(String authorization) {
        DecodedJWT jwt = verifyAuthorization(authorization);
        if (!ACCOUNT_AUTH_TYPE.equals(jwt.getClaim(AUTH_TYPE_CLAIM).asString())) {
            return;
        }
        UserAccountEntity account = verifyAccountSession(jwt);
        if (1 != userAccountRepository.revokeTokens(account.getUserId(), account.getTokenVersion())) {
            throw new AppException(ResponseCode.AUTH_REQUIRED.getCode(), "登录状态已变化，请重新登录");
        }
    }

    @Override
    public void changePassword(String authorization, String currentPassword, String newPassword) {
        DecodedJWT jwt = verifyAuthorization(authorization);
        if (!ACCOUNT_AUTH_TYPE.equals(jwt.getClaim(AUTH_TYPE_CLAIM).asString())) {
            throw new AppException(ResponseCode.AUTH_REQUIRED.getCode(), "请使用商城账号登录后修改密码");
        }
        UserAccountEntity account = verifyAccountSession(jwt);
        if (null == currentPassword || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new AppException(ResponseCode.PASSWORD_CHANGE_ERROR.getCode(), "当前密码错误");
        }
        validateNewPassword(newPassword);
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new AppException(ResponseCode.PASSWORD_CHANGE_ERROR.getCode(), "新密码不能与当前密码相同");
        }
        String newPasswordHash = passwordEncoder.encode(newPassword);
        if (1 != userAccountRepository.updatePassword(
                account.getUserId(), newPasswordHash, account.getTokenVersion())) {
            throw new AppException(ResponseCode.PASSWORD_CHANGE_ERROR.getCode(), "密码修改失败，请重新登录后再试");
        }
    }

    @Override
    public String createQrCodeTicket() {
        try{
            return loginPort.createQrCodeTicket();
        } catch (Exception e){
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "生成微信二维码失败", e);
        }
    }

    @Override
    public String createQrCodeTicket(String sceneStr) {
        String normalizedSceneStr = normalizeSceneStr(sceneStr);
        try {
            String ticket = loginPort.createQrCodeTicket(normalizedSceneStr);
            if (null == ticket || ticket.isBlank()) {
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "微信未返回二维码 ticket");
            }
            ticket = ticket.trim();
            sceneTicket.put(normalizedSceneStr, ticket);
            ticketScene.put(ticket, normalizedSceneStr);
            return ticket;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "生成微信二维码失败", e);
        }
    }

    @Override
    public String checkLogin(String ticket) {
        String normalizedTicket = normalizeTicket(ticket);
        // 新接口生成的 ticket 必须携带 sceneStr 校验，不能通过旧接口绕过场景绑定。
        if (null != ticketScene.getIfPresent(normalizedTicket)) {
            return null;
        }
        return issueLoginToken(normalizedTicket);
    }

    @Override
    public String checkLogin(String ticket, String sceneStr) {
        String normalizedTicket = normalizeTicket(ticket);
        String normalizedSceneStr = normalizeSceneStr(sceneStr);
        String expectedTicket = sceneTicket.getIfPresent(normalizedSceneStr);
        String expectedSceneStr = ticketScene.getIfPresent(normalizedTicket);

        if (!normalizedTicket.equals(expectedTicket)
                || !normalizedSceneStr.equals(expectedSceneStr)) {
            return null;
        }

        return issueLoginToken(normalizedTicket);
    }

    private String issueLoginToken(String ticket) {
        // 通过 ticket 判断用户是否完成扫码；完成后签发短期登录令牌，不直接暴露 openid 作为凭证。
        String openid = openidToken.getIfPresent(ticket);
        if (null == openid) return null;
        return issueJwt(openid, WEIXIN_AUTH_TYPE, null);
    }

    @Override
    public String resolveUserId(String authorization) {
        DecodedJWT jwt = verifyAuthorization(authorization);
        if (ACCOUNT_AUTH_TYPE.equals(jwt.getClaim(AUTH_TYPE_CLAIM).asString())) {
            return verifyAccountSession(jwt).getUserId();
        }
        return requireSubject(jwt);
    }

    private DecodedJWT verifyAuthorization(String authorization) {
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
            return JWT.require(Algorithm.HMAC256(jwtSecret))
                    .build()
                    .verify(token);
        } catch (JWTVerificationException e) {
            throw new AppException(ResponseCode.AUTH_REQUIRED.getCode(), "登录令牌无效或已过期", e);
        }
    }

    private UserAccountEntity verifyAccountSession(DecodedJWT jwt) {
        String userId = requireSubject(jwt);
        Integer tokenVersion = jwt.getClaim(TOKEN_VERSION_CLAIM).asInt();
        UserAccountEntity account = userAccountRepository.queryByUserId(userId);
        if (null == account
                || !account.isEnabled()
                || null == tokenVersion
                || !tokenVersion.equals(account.getTokenVersion())) {
            throw new AppException(ResponseCode.AUTH_REQUIRED.getCode(), "登录令牌无效或已过期");
        }
        return account;
    }

    private String requireSubject(DecodedJWT jwt) {
        String userId = jwt.getSubject();
        if (null == userId || userId.isBlank()) {
            throw new AppException(ResponseCode.AUTH_REQUIRED.getCode(), ResponseCode.AUTH_REQUIRED.getInfo());
        }
        return userId;
    }

    private String issueJwt(String userId, String authType, Integer tokenVersion) {
        if (null == jwtSecret || jwtSecret.isBlank()) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "AUTH_JWT_SECRET未配置");
        }
        Instant now = Instant.now();
        var builder = JWT.create()
                .withSubject(userId)
                .withClaim(AUTH_TYPE_CLAIM, authType)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(1, ChronoUnit.HOURS)));
        if (null != tokenVersion) {
            builder.withClaim(TOKEN_VERSION_CLAIM, tokenVersion);
        }
        return builder.sign(Algorithm.HMAC256(jwtSecret));
    }

    @Override
    public void saveLoginState(String ticket, String openid) throws IOException {
        String normalizedTicket = normalizeTicket(ticket);
        if (null == openid || openid.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "openid不能为空");
        }
        // 微信回调只保存扫码结果，正式 JWT 在浏览器轮询成功时签发。
        openidToken.put(normalizedTicket, openid.trim());
        // 发送登录成功模板消息
        loginPort.sendLoginTempleteMessage(openid.trim());
    }

    private String normalizeSceneStr(String sceneStr) {
        if (null == sceneStr || sceneStr.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "sceneStr不能为空");
        }
        String normalizedSceneStr = sceneStr.trim().toUpperCase(Locale.ROOT);
        if (!SCENE_STR_PATTERN.matcher(normalizedSceneStr).matches()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "sceneStr只能包含字母、数字、下划线或短横线，长度为1到64位");
        }
        return normalizedSceneStr;
    }

    private String normalizeTicket(String ticket) {
        if (null == ticket || ticket.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "ticket不能为空");
        }
        return ticket.trim();
    }

    private String normalizeUsername(String username) {
        if (null == username || username.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "用户名不能为空");
        }
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "用户名只能包含小写字母、数字或下划线，长度为4到32位");
        }
        return normalizedUsername;
    }

    private void validateNewPassword(String password) {
        if (null == password
                || password.length() < 8
                || password.length() > 64
                || !PASSWORD_LETTER_PATTERN.matcher(password).matches()
                || !PASSWORD_NUMBER_PATTERN.matcher(password).matches()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "密码长度为8到64位，且必须同时包含字母和数字");
        }
    }

    private AppException credentialError() {
        return new AppException(
                ResponseCode.ACCOUNT_CREDENTIAL_ERROR.getCode(),
                ResponseCode.ACCOUNT_CREDENTIAL_ERROR.getInfo());
    }

}
