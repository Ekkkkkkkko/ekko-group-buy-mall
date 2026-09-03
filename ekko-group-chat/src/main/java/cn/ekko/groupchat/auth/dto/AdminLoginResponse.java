package cn.ekko.groupchat.auth.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 管理员登录响应体，返回账号名与访问令牌。
 */
@Getter
@RequiredArgsConstructor
public class AdminLoginResponse {

    private final String username;
    private final String token;

}
