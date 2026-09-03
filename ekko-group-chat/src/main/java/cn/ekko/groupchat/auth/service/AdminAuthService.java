package cn.ekko.groupchat.auth.service;

import cn.ekko.groupchat.auth.dto.AdminLoginResponse;
import cn.ekko.groupchat.auth.entity.AdminUser;
import cn.ekko.groupchat.auth.exception.AdminAuthenticationException;
import cn.ekko.groupchat.auth.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * 管理员认证服务。
 *
 * <p>负责登录校验与令牌管理：登录时验证账号密码并返回内存中生成的会话令牌；
 * 拦截器调用 {@link #requireValidToken} 校验 Bearer 令牌（常量时间比较防时序攻击）。
 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final String sessionToken = UUID.randomUUID().toString().replace("-", "");

    public AdminLoginResponse login(String username, String password) {
        AdminUser admin = adminUserMapper.selectByUsername(username);
        if (admin == null
                || !Boolean.TRUE.equals(admin.getEnabled())
                || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new AdminAuthenticationException("管理员账号或密码错误");
        }
        return new AdminLoginResponse(admin.getUsername(), sessionToken);
    }

    public void requireValidToken(String authorization) {
        String expected = "Bearer " + sessionToken;
        if (!secureEquals(expected, authorization)) {
            throw new AdminAuthenticationException("管理员登录已失效，请重新登录");
        }
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
