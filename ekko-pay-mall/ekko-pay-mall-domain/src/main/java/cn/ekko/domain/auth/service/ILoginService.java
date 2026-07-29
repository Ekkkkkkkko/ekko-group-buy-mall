package cn.ekko.domain.auth.service;

import java.io.IOException;

/**
 * @author Ekko
 * @description 微信服务
 */
public interface ILoginService {

    String createQrCodeTicket();

    String checkLogin(String ticket);

    /** 校验 Bearer 登录令牌并返回令牌中的用户ID。 */
    String resolveUserId(String authorization);

    void saveLoginState(String ticket, String openid) throws IOException;

}
