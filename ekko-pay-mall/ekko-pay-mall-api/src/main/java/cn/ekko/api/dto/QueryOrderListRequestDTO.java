package cn.ekko.api.dto;

import lombok.Data;

@Data
public class QueryOrderListRequestDTO {

    /**
     * 仅用于与登录身份做一致性校验，服务端实际使用登录令牌中的用户ID。
     */
    private String userId;

    /** 上一页最后一条记录的自增ID；首页为空。 */
    private Long lastId;

    /** 每页条数。 */
    private Integer pageSize;
}
