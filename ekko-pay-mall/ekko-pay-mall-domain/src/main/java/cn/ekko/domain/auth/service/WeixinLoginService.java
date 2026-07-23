package cn.ekko.domain.auth.service;

import cn.ekko.domain.auth.adapter.port.ILoginPort;
import cn.ekko.types.exception.AppException;
import com.google.common.cache.Cache;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.IOException;

/**
 * @author Ekko
 * @description 微信服务
 */
@Service
public class WeixinLoginService implements ILoginService {

    @Resource
    private ILoginPort loginPort;
    @Resource
    private Cache<String, String> openidToken;

    @Override
    public String createQrCodeTicket() {
        try{
            return loginPort.createQrCodeTicket();
        } catch (Exception e){
            throw new AppException(e.getMessage());
        }
    }

    @Override
    public String checkLogin(String ticket) {
        // 通过 ticket 判断，用户是否登录。如果登录了，会在内存里写入信息。
        return openidToken.getIfPresent(ticket);
    }

    @Override
    public void saveLoginState(String ticket, String openid) throws IOException {
        // 实际的业务场景，openid 可以生成 jwt 的 token 让前端存储
        openidToken.put(ticket, openid);
        // 发送登录成功模板消息
        loginPort.sendLoginTempleteMessage(openid);
    }

}
