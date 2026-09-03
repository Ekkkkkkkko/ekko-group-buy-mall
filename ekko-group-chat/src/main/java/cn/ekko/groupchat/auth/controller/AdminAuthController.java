package cn.ekko.groupchat.auth.controller;

import cn.ekko.groupchat.auth.dto.AdminLoginRequest;
import cn.ekko.groupchat.auth.dto.AdminLoginResponse;
import cn.ekko.groupchat.auth.service.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员登录控制器，提供 {@code POST /api/v1/admin/login} 接口，
 * 校验账号密码后返回访问令牌，供后续文档管理接口鉴权使用。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService authService;

    /**
     * 管理员登录。
     * <p>校验账号密码，通过后签发访问令牌；后续文档管理接口需携带该令牌鉴权。
     *
     * @param request 登录请求，包含用户名与密码
     * @return 登录结果，含访问令牌
     */
    @PostMapping("/login")
    public AdminLoginResponse login(@Valid @RequestBody AdminLoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword());
    }
}
