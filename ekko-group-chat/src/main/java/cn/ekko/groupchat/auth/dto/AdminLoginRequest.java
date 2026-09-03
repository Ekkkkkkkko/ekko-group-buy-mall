package cn.ekko.groupchat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理员登录请求体，携带账号与密码，均不可为空。
 */
@Getter
@Setter
public class AdminLoginRequest {

    @NotBlank(message = "请输入管理员账号")
    private String username;

    @NotBlank(message = "请输入管理员密码")
    private String password;

}
