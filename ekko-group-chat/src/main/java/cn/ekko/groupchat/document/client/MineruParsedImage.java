package cn.ekko.groupchat.document.client;

/** MinerU 结果 ZIP 中提取出的单张图片。 */
public record MineruParsedImage(
        String sourcePath,
        String fileName,
        String contentType,
        String extension,
        byte[] content,
        String sha256
) {
}
