package cn.ekko.groupchat.document.client;

import cn.ekko.groupchat.document.service.image.KnowledgeImageMarkdown;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 为向量化增加产品身份上下文，同时保持 ES 中的展示正文干净。 */
@Component
public class EmbeddingTextBuilder {

    public String build(String title, String productModel, String headingPath, String displayText) {
        List<String> identity = new ArrayList<>(3);
        if (StringUtils.hasText(productModel)) {
            identity.add("产品型号：" + productModel.trim());
        }
        if (StringUtils.hasText(title)) {
            identity.add("文档标题：" + title.trim());
        }
        if (StringUtils.hasText(headingPath)) {
            identity.add("章节：" + headingPath.trim());
        }
        String semanticText = KnowledgeImageMarkdown.withoutImageTargets(displayText);
        if (identity.isEmpty()) {
            return semanticText;
        }
        return String.join("\n", identity) + "\n\n" + semanticText;
    }
}
