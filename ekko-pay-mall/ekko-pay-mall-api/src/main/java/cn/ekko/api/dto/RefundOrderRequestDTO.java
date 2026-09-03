package cn.ekko.api.dto;

import lombok.Data;

@Data
public class RefundOrderRequestDTO {

    /**
     * 仅用于与登录身份做一致性校验，服务端实际使用登录令牌中的用户ID。
     */
    private String userId;

    private String orderId;
}
