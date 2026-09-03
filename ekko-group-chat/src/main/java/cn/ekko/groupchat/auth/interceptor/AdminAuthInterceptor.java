package cn.ekko.groupchat.auth.interceptor;

import cn.ekko.groupchat.auth.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员鉴权拦截器，校验请求头中的 Bearer 令牌。
 *
 * <p>放行 CORS 预检的 OPTIONS 请求；其余请求委托 {@link AdminAuthService#requireValidToken}
 * 校验，令牌无效时抛异常中断请求。
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminAuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        authService.requireValidToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        return true;
    }
}
