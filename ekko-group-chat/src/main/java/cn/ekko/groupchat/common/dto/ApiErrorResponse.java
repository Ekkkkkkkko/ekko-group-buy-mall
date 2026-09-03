package cn.ekko.groupchat.common.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 统一 API 错误响应体，包含时间戳、HTTP 状态码、错误描述、
 * 提示信息及请求路径。
 */
@Getter
@RequiredArgsConstructor
public class ApiErrorResponse {

    private final OffsetDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;

}
