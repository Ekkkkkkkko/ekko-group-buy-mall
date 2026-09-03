package cn.ekko.trigger.http;

import cn.ekko.domain.auth.service.ILoginService;
import cn.ekko.types.sdk.weixin.MessageTextEntity;
import cn.ekko.types.sdk.weixin.SignatureUtil;
import cn.ekko.types.sdk.weixin.XmlUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * @author Ekko
 * @description 微信服务对接，对接地址：<a href="http://xfg-studio.natapp1.cc/api/v1/weixin/portal/receive">/api/v1/weixin/portal/receive</a>
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/weixin/portal/")
public class WeixinPortalController {

    @Value("${weixin.config.originalid}")
    private String originalid;
    @Value("${weixin.config.token:}")
    private String weixinToken;
    @Resource
    private ILoginService loginService;

    /** 微信公众号服务器配置验签。 */
    @GetMapping(value = "receive", produces = "text/plain;charset=utf-8")
    public String validate(@RequestParam(value = "signature", required = false) String signature,
                           @RequestParam(value = "timestamp", required = false) String timestamp,
                           @RequestParam(value = "nonce", required = false) String nonce,
                           @RequestParam(value = "echostr", required = false) String echostr) {
        try {
            log.info("微信公众号验签开始");
            if (StringUtils.isAnyBlank(weixinToken, signature, timestamp, nonce, echostr)) {
                throw new IllegalArgumentException("请求参数非法，请核实!");
            }
            boolean check = SignatureUtil.check(weixinToken, signature, timestamp, nonce);
            log.info("微信公众号验签完成 check:{}", check);
            if (!check) {
                return null;
            }
            return echostr;
        } catch (Exception e) {
            log.error("微信公众号验签失败", e);
            return null;
        }
    }

    /**
     * 回调，接收公众号消息【扫描登录，会接收到消息】
     */
    @PostMapping(value = "receive", produces = "application/xml; charset=UTF-8")
    public String post(@RequestBody String requestBody,
                       @RequestParam("signature") String signature,
                       @RequestParam("timestamp") String timestamp,
                       @RequestParam("nonce") String nonce,
                       @RequestParam("openid") String openid,
                       @RequestParam(name = "encrypt_type", required = false) String encType,
                       @RequestParam(name = "msg_signature", required = false) String msgSignature) {
        try {
            if (StringUtils.isBlank(weixinToken)) {
                log.error("微信公众号回调 token 未配置");
                return "";
            }
            if (!SignatureUtil.check(weixinToken, signature, timestamp, nonce)) {
                log.warn("拒绝未通过签名校验的微信公众号回调");
                return "";
            }
            // 消息转换
            MessageTextEntity message = XmlUtil.xmlToBean(requestBody, MessageTextEntity.class);

            // 已关注用户扫码为 SCAN；未关注用户扫码关注后为 subscribe，两者都会携带 Ticket。
            boolean qrCodeLoginEvent = "event".equals(message.getMsgType())
                    && ("SCAN".equals(message.getEvent()) || "subscribe".equals(message.getEvent()))
                    && StringUtils.isNotBlank(message.getTicket());
            if (qrCodeLoginEvent) {
                // 保存登录状态
                loginService.saveLoginState(message.getTicket(), openid);
                log.info("微信公众号扫码登录状态保存成功 event:{}", message.getEvent());
                return buildMessageTextEntity(openid, "登录成功");
            }

            log.info("微信公众号回调不是扫码登录事件 event:{}", message.getEvent());
            return buildMessageTextEntity(openid, "测试本案例，需要请扫码登录！");
        } catch (Exception e) {
            log.error("处理微信公众号回调失败", e);
            return "";
        }
    }

    private String buildMessageTextEntity(String openid, String content) {
        MessageTextEntity res = new MessageTextEntity();
        // 公众号分配的ID
        res.setFromUserName(originalid);
        res.setToUserName(openid);
        res.setCreateTime(String.valueOf(System.currentTimeMillis() / 1000L));
        res.setMsgType("text");
        res.setContent(content);
        return XmlUtil.beanToXml(res);
    }

}
