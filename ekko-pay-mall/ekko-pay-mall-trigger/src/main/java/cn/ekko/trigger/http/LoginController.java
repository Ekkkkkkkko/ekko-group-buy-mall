package cn.ekko.trigger.http;

import cn.ekko.api.response.Response;
import cn.ekko.api.dto.AccountLoginRequestDTO;
import cn.ekko.api.dto.AccountRegisterRequestDTO;
import cn.ekko.api.dto.ChangePasswordRequestDTO;
import cn.ekko.domain.auth.service.ILoginService;
import cn.ekko.types.common.Constants;
import cn.ekko.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/login/")
public class LoginController {

    @Resource
    private ILoginService loginService;

    /** 注册商城账号；注册成功后直接返回 JWT。 */
    @PostMapping("account/register")
    public Response<String> register(@RequestBody(required = false) AccountRegisterRequestDTO request) {
        try {
            if (null == request) {
                throw new AppException(Constants.ResponseCode.ILLEGAL_PARAMETER.getCode(), "注册信息不能为空");
            }
            String loginToken = loginService.register(request.getUsername(), request.getPassword());
            log.info("商城账号注册成功");
            return success("注册成功", loginToken);
        } catch (AppException e) {
            log.warn("商城账号注册失败 code:{}", e.getCode());
            return failed(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("商城账号注册失败", e);
            return failed(Constants.ResponseCode.UN_ERROR.getCode(), Constants.ResponseCode.UN_ERROR.getInfo());
        }
    }

    /** 使用商城账号和密码登录。 */
    @PostMapping("account/login")
    public Response<String> accountLogin(@RequestBody(required = false) AccountLoginRequestDTO request) {
        try {
            if (null == request) {
                throw new AppException(Constants.ResponseCode.ILLEGAL_PARAMETER.getCode(), "登录信息不能为空");
            }
            String loginToken = loginService.login(request.getUsername(), request.getPassword());
            log.info("商城账号登录成功");
            return success("登录成功", loginToken);
        } catch (AppException e) {
            log.warn("商城账号登录失败 code:{}", e.getCode());
            return failed(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("商城账号登录失败", e);
            return failed(Constants.ResponseCode.UN_ERROR.getCode(), Constants.ResponseCode.UN_ERROR.getInfo());
        }
    }

    /** 服务端退出；账号 tokenVersion 递增后，当前账号已有 JWT 均失效。 */
    @PostMapping("account/logout")
    public Response<String> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            loginService.logout(authorization);
            log.info("商城账号退出成功");
            return success("退出成功", null);
        } catch (AppException e) {
            log.warn("商城账号退出失败 code:{}", e.getCode());
            return failed(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("商城账号退出失败", e);
            return failed(Constants.ResponseCode.UN_ERROR.getCode(), Constants.ResponseCode.UN_ERROR.getInfo());
        }
    }

    /** 修改当前商城账号的密码；成功后要求重新登录。 */
    @PutMapping("account/password")
    public Response<String> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ChangePasswordRequestDTO request) {
        try {
            if (null == request) {
                throw new AppException(Constants.ResponseCode.ILLEGAL_PARAMETER.getCode(), "密码信息不能为空");
            }
            loginService.changePassword(authorization, request.getCurrentPassword(), request.getNewPassword());
            log.info("商城账号密码修改成功");
            return success("密码修改成功，请重新登录", null);
        } catch (AppException e) {
            log.warn("商城账号密码修改失败 code:{}", e.getCode());
            return failed(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("商城账号密码修改失败", e);
            return failed(Constants.ResponseCode.UN_ERROR.getCode(), Constants.ResponseCode.UN_ERROR.getInfo());
        }
    }

    /**
     * 获取微信 ticket 凭证
     * <a href="http://xfg-studio.natapp1.cc/api/v1/login/weixin_qrcode_ticket">/api/v1/login/weixin_qrcode_ticket</a>
     */
    @Deprecated
    @RequestMapping(value = "weixin_qrcode_ticket", method = RequestMethod.GET)
    public Response<String> weixinQrCodeTicket() {
        try {
            String qrCodeTicket = loginService.createQrCodeTicket();
            log.info("生成兼容版微信扫码登录二维码成功");
            return Response.<String>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(qrCodeTicket)
                    .build();
        } catch (AppException e) {
            log.warn("生成兼容版微信扫码登录二维码失败 code:{}", e.getCode());
            return failed(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("生成兼容版微信扫码登录二维码失败", e);
            return Response.<String>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 轮训登录
     * <a href="http://xfg-studio.natapp1.cc/api/v1/login/check_login">/api/v1/login/check_login</a>
     */
    @Deprecated
    @RequestMapping(value = "check_login", method = RequestMethod.GET)
    public Response<String> checkLogin(@RequestParam String ticket) {
        try {
            String loginToken = loginService.checkLogin(ticket);
            log.info("兼容版扫码登录检测完成 loginSuccess:{}", StringUtils.isNotBlank(loginToken));
            if (StringUtils.isNotBlank(loginToken)) {
                return Response.<String>builder()
                        .code(Constants.ResponseCode.SUCCESS.getCode())
                        .info(Constants.ResponseCode.SUCCESS.getInfo())
                        .data(loginToken)
                        .build();
            } else {
                return Response.<String>builder()
                        .code(Constants.ResponseCode.NO_LOGIN.getCode())
                        .info(Constants.ResponseCode.NO_LOGIN.getInfo())
                        .build();
            }
        } catch (AppException e) {
            log.warn("兼容版扫码登录检测失败 code:{}", e.getCode());
            return failed(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("兼容版扫码登录检测失败", e);
            return Response.<String>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /** 获取与浏览器场景绑定的微信二维码 ticket。 */
    @GetMapping("weixin_qrcode_ticket_scene")
    public Response<String> weixinQrCodeTicketScene(@RequestParam String sceneStr) {
        try {
            String qrCodeTicket = loginService.createQrCodeTicket(sceneStr);
            log.info("生成场景绑定微信扫码登录二维码成功");
            return Response.<String>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(qrCodeTicket)
                    .build();
        } catch (AppException e) {
            log.warn("生成场景绑定微信扫码登录二维码失败 code:{}", e.getCode());
            return failed(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("生成场景绑定微信扫码登录二维码失败", e);
            return failed(Constants.ResponseCode.UN_ERROR.getCode(), Constants.ResponseCode.UN_ERROR.getInfo());
        }
    }

    /** 同时校验 ticket 与浏览器场景后轮询登录结果。 */
    @GetMapping("check_login_scene")
    public Response<String> checkLoginScene(@RequestParam String ticket,
                                            @RequestParam String sceneStr) {
        try {
            String loginToken = loginService.checkLogin(ticket, sceneStr);
            boolean loginSuccess = StringUtils.isNotBlank(loginToken);
            log.info("场景绑定扫码登录检测完成 loginSuccess:{}", loginSuccess);
            if (!loginSuccess) {
                return failed(Constants.ResponseCode.NO_LOGIN.getCode(), Constants.ResponseCode.NO_LOGIN.getInfo());
            }
            return Response.<String>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(loginToken)
                    .build();
        } catch (AppException e) {
            log.warn("场景绑定扫码登录检测失败 code:{}", e.getCode());
            return failed(e.getCode(), e.getInfo());
        } catch (Exception e) {
            log.error("场景绑定扫码登录检测失败", e);
            return failed(Constants.ResponseCode.UN_ERROR.getCode(), Constants.ResponseCode.UN_ERROR.getInfo());
        }
    }

    private Response<String> failed(String code, String info) {
        return Response.<String>builder()
                .code(StringUtils.defaultIfBlank(code, Constants.ResponseCode.UN_ERROR.getCode()))
                .info(StringUtils.defaultIfBlank(info, Constants.ResponseCode.UN_ERROR.getInfo()))
                .build();
    }

    private Response<String> success(String info, String data) {
        return Response.<String>builder()
                .code(Constants.ResponseCode.SUCCESS.getCode())
                .info(info)
                .data(data)
                .build();
    }

}
