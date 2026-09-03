package cn.ekko.groupchat.common;

import cn.ekko.groupchat.auth.exception.AdminAuthenticationException;
import cn.ekko.groupchat.common.dto.ApiErrorResponse;
import cn.ekko.groupchat.document.exception.DocumentNotFoundException;
import cn.ekko.groupchat.document.exception.DocumentProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

/**
 * 全局异常处理器，将各类异常统一转换为 {@link ApiErrorResponse} JSON 响应。
 *
 * <p>状态码映射：鉴权失败 401、参数/校验错误 400、文档不存在 404、
 * 文档处理失败 502、其余未知异常 500。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AdminAuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleUnauthorized(
            AdminAuthenticationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(DocumentNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentProcessingException.class)
    ResponseEntity<ApiErrorResponse> handleProcessing(DocumentProcessingException exception, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_GATEWAY,
                exception.getMessage() + "，documentId=" + exception.getDocumentId(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务处理失败", request);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()
        ));
    }
}
