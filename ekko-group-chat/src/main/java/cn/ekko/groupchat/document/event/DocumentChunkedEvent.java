package cn.ekko.groupchat.document.event;

/** 分片已完整落入 MySQL，可以开始向量化并发布。 */
public record DocumentChunkedEvent(long documentId) {
}
