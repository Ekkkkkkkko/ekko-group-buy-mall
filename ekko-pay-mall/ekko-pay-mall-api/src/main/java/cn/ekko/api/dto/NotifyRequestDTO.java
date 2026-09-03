package cn.ekko.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 拼团成团通知请求。
 */
@Data
public class NotifyRequestDTO {

    private String teamId;

    private List<String> outTradeNoList;

}
