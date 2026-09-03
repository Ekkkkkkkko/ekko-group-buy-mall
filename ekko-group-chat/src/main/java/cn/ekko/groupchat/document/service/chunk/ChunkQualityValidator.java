package cn.ekko.groupchat.document.service.chunk;

import cn.ekko.groupchat.config.GroupChatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 在写入 MySQL/ES 前拦截空块、标题块和被切断的 HTML 表格。 */
@Component
@RequiredArgsConstructor
public class ChunkQualityValidator {

    private static final Pattern HEADING_ONLY = Pattern.compile("(?m)^(?:#{1,6}\\s+[^\\n]+\\s*)+$");
    private static final Pattern TABLE_TAG = Pattern.compile("(?i)</?(?:table|thead|tbody|tr|td|th)\\b");
    private static final Pattern TABLE_OPEN = Pattern.compile("(?i)<table\\b");
    private static final Pattern TABLE_CLOSE = Pattern.compile("(?i)</table>");
    private static final Pattern MEANINGFUL = Pattern.compile("[\\p{L}\\p{N}]");

    private final GroupChatProperties properties;

    public void validate(List<DocumentChunk> chunks) {
        validate(chunks, false);
    }

    public void validate(List<DocumentChunk> chunks, boolean allowCompleteHtmlTables) {
        if (chunks == null || chunks.stream().noneMatch(DocumentChunk::isSearchable)) {
            throw new IllegalStateException("预处理后没有可建立索引的有效分片");
        }

        Set<String> ids = new HashSet<>();
        int expectedSearchableIndex = 0;
        for (DocumentChunk chunk : chunks) {
            if (!StringUtils.hasText(chunk.getChunkId()) || !ids.add(chunk.getChunkId())) {
                throw new IllegalStateException("分片 ID 为空或重复");
            }
            if (!StringUtils.hasText(chunk.getText())) {
                throw new IllegalStateException("存在空分片: " + chunk.getChunkId());
            }
            if (!chunk.isSearchable()) {
                continue;
            }
            if (chunk.getChunkIndex() != expectedSearchableIndex++) {
                throw new IllegalStateException("可检索分片序号不连续: " + chunk.getChunkId());
            }
            String text = chunk.getText().trim();
            if (HEADING_ONLY.matcher(text).matches()) {
                throw new IllegalStateException("存在只有 Markdown 标题的无效分片: " + chunk.getChunkId());
            }
            if (TABLE_TAG.matcher(text).find()) {
                if (!allowCompleteHtmlTables
                        || TABLE_OPEN.matcher(text).results().count()
                        != TABLE_CLOSE.matcher(text).results().count()) {
                    throw new IllegalStateException(
                            "分片中包含未转换或不完整的 HTML 表格: " + chunk.getChunkId()
                    );
                }
            }
            long meaningfulChars = MEANINGFUL.matcher(text).results().count();
            if (meaningfulChars < properties.getRag().getMinMeaningfulChars()) {
                throw new IllegalStateException("分片有效字符过少: " + chunk.getChunkId());
            }
        }
    }
}
