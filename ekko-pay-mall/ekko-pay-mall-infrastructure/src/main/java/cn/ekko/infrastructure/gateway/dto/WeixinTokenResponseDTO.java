package cn.ekko.infrastructure.gateway.dto;

import lombok.Data;

/**
 * @author Ekko
 * @description 获取 Access token DTO 对象
 */
@Data
public class WeixinTokenResponseDTO {

    private String access_token;
    private int expires_in;
    private String errcode;
    private String errmsg;

}
