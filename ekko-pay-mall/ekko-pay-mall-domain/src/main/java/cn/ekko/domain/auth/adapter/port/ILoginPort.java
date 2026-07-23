package cn.ekko.domain.auth.adapter.port;

import java.io.IOException;

/**
 * @author Ekko
 * @description 登录适配器接口
 */
public interface ILoginPort {

    String createQrCodeTicket() throws IOException;

    void sendLoginTempleteMessage(String openid) throws IOException;

}
