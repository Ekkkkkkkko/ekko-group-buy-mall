package cn.ekko.groupchat.document.event;

/** 文档已转换为 Markdown，可以开始切片。 */
public record DocumentConvertedEvent(long documentId) {
}
