package cn.ekko.domain.auth.service;

import java.io.IOException;

/**
 * @author Ekko
 * @description 商城登录服务
 */
public interface ILoginService {

    /** 注册商城账号并返回 JWT。 */
    String register(String username, String password);

    /** 使用账号密码登录并返回 JWT。 */
    String login(String username, String password);

    /** 服务端退出；账号的当前版本 JWT 会立即失效。 */
    void logout(String authorization);

    /** 修改密码；成功后当前账号已签发的 JWT 会立即失效。 */
    void changePassword(String authorization, String currentPassword, String newPassword);

    String createQrCodeTicket();

    String createQrCodeTicket(String sceneStr);

    String checkLogin(String ticket);

    String checkLogin(String ticket, String sceneStr);

    /** 校验 Bearer 登录令牌并返回令牌中的用户ID。 */
    String resolveUserId(String authorization);

    void saveLoginState(String ticket, String openid) throws IOException;

}
