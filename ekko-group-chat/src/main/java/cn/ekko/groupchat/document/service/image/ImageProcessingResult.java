package cn.ekko.groupchat.document.service.image;

/** 图片持久化和 Markdown 改写后的结果。 */
public record ImageProcessingResult(String processedMarkdown, int imageCount) {
}
