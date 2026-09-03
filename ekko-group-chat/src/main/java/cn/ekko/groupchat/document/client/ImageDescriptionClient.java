package cn.ekko.groupchat.document.client;

import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

/** 使用视觉模型把图片转换为可检索、可引用的文字说明。 */
@Component
public class ImageDescriptionClient {

    private final ChatModel visionChatModel;

    public ImageDescriptionClient(@Qualifier("visionChatModel") ChatModel visionChatModel) {
        this.visionChatModel = visionChatModel;
    }

    public String describe(KnowledgeDocument document, String altText, String signedUrl) {
        String prompt = """
                请分析这张产品资料图片，只描述图片中能够确认的信息，不要推测。
                输出一段适合知识库检索的中文纯文本，优先说明：图片类型、产品型号、接口、按钮、
                指示灯、文字、参数、布局和连接关系。不要使用 Markdown，不要输出多余说明。

                文档标题：%s
                产品型号：%s
                原始图片说明：%s
                """.formatted(
                safe(document.getTitle()),
                safe(document.getProductModel()),
                safe(altText)
        );
        String description = visionChatModel.chat(UserMessage.from(
                        TextContent.from(prompt),
                        ImageContent.from(URI.create(signedUrl))
                ))
                .aiMessage()
                .text();
        if (!StringUtils.hasText(description)) {
            throw new IllegalStateException("视觉模型返回空图片描述");
        }
        return description.replaceAll("\\s+", " ").trim();
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未提供";
    }
}
