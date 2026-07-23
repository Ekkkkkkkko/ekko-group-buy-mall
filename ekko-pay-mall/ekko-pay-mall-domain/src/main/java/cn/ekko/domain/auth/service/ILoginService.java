package cn.ekko.domain.auth.service;

import java.io.IOException;

/**
 * @author Ekko
 * @description 微信服务
 */
public interface ILoginService {

    String createQrCodeTicket();

    String checkLogin(String ticket);

    void saveLoginState(String ticket, String openid) throws IOException;

}
