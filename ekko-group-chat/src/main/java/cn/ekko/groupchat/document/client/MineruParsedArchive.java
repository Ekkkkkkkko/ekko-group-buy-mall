package cn.ekko.groupchat.document.client;

import java.util.List;

/** MinerU 结果 ZIP 的结构化内容：原始 Markdown、Markdown 路径和图片资源。 */
public record MineruParsedArchive(
        String markdownPath,
        String markdown,
        List<MineruParsedImage> images
) {

    public MineruParsedArchive {
        images = List.copyOf(images);
    }
}
