package cn.ekko.groupchat.document.exception;

/**
 * 文档不存在异常，按 ID 查不到文档时抛出，
 * 由全局异常处理器转换为 404 响应。
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(long documentId) {
        super("文档不存在: " + documentId);
    }
}
