package cn.ekko.groupchat.auth.exception;

/**
 * 管理员鉴权失败异常，在登录失败或令牌无效时抛出，
 * 由全局异常处理器统一转换为 401 响应。
 */
public class AdminAuthenticationException extends RuntimeException {

    public AdminAuthenticationException(String message) {
        super(message);
    }
}
