package cn.ekko.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupBuyMarketResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务响应码，0000 表示成功
     */
    private String code;

    /**
     * 响应说明
     */
    private String info;

    /**
     * 业务数据
     */
    private T data;
}