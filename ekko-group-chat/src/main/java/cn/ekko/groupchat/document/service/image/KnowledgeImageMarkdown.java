package cn.ekko.groupchat.document.service.image;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Markdown 图片标签的改写、ID 提取和向量文本清洗工具。 */
public final class KnowledgeImageMarkdown {

    private static final Pattern IMAGE_TAG = Pattern.compile("!\\[([^]\\n]*)]\\(([^)\\s]+)(?:\\s+[^)]*)?\\)");
    private static final Pattern STORED_IMAGE = Pattern.compile("!\\[([^]\\n]*)]\\(knowledge-image://(\\d+)\\)");

    private KnowledgeImageMarkdown() {
    }

    public static List<ImageTag> imageTags(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }
        List<ImageTag> tags = new ArrayList<>();
        Matcher matcher = IMAGE_TAG.matcher(markdown);
        while (matcher.find()) {
            tags.add(new ImageTag(matcher.group(1).trim(), unwrap(matcher.group(2))));
        }
        return List.copyOf(tags);
    }

    public static String rewrite(String markdown, BiFunction<String, String, String> replacement) {
        Matcher matcher = IMAGE_TAG.matcher(markdown);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = replacement.apply(matcher.group(1).trim(), unwrap(matcher.group(2)));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static List<Long> imageIds(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        Matcher matcher = STORED_IMAGE.matcher(text);
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(2)));
        }
        return List.copyOf(ids);
    }

    /** Embedding 与来源正文均不保留 URL/对象哈希，只保留有语义的图片说明。 */
    public static String withoutImageTargets(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return rewrite(text, (alt, target) -> StringUtils.hasText(alt) ? "图片：" + alt : "");
    }

    private static String unwrap(String target) {
        String value = target.trim();
        if (value.startsWith("<") && value.endsWith(">") && value.length() > 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public record ImageTag(String altText, String target) {
    }
}
