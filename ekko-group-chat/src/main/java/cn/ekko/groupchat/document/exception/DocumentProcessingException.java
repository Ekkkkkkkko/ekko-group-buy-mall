package cn.ekko.groupchat.document.exception;

import lombok.Getter;

/**
 * 文档处理失败异常，携带 documentId 便于定位问题，
 * 由全局异常处理器转换为 502 响应。
 */
@Getter
public class DocumentProcessingException extends RuntimeException {

    private final long documentId;

    public DocumentProcessingException(long documentId, String message, Throwable cause) {
        super(message, cause);
        this.documentId = documentId;
    }

}
