package cn.ekko.groupchat.document.service.chunk.strategy;

import cn.ekko.groupchat.document.service.chunk.ChunkType;
import cn.ekko.groupchat.document.service.chunk.ChunkingRequest;
import cn.ekko.groupchat.document.service.chunk.ChunkingStrategy;
import cn.ekko.groupchat.document.service.chunk.ChunkingStrategyType;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import cn.ekko.groupchat.document.service.chunk.TextChunkSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 Markdown 标题形成逻辑章节；超长章节切为共享 brotherChunkId 的兄弟分片。
 * 检索命中任一兄弟后，可按 brotherChunkIndex 顺序恢复完整同级上下文。
 */
@Component
public class MarkdownBrotherChunkingStrategy implements ChunkingStrategy {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");

    @Override
    public ChunkingStrategyType type() {
        return ChunkingStrategyType.BROTHER;
    }

    @Override
    public List<DocumentChunk> split(ChunkingRequest request) {
        List<Section> sections = parseSections(
                TextChunkSupport.normalize(request.getMarkdown()), request.getTitleLevel()
        );
        List<DocumentChunk> result = new ArrayList<>();
        int chunkIndex = 0;
        int brotherGroupIndex = 0;
        for (Section section : sections) {
            if (!StringUtils.hasText(section.text())) {
                continue;
            }
            List<String> parts = TextChunkSupport.splitNaturally(
                    section.text(), request.getChunkSize(), request.getChunkOverlap()
            );
            String brotherChunkId = parts.size() > 1
                    ? "doc-" + request.getDocumentId() + "-brother-" + brotherGroupIndex++
                    : null;
            for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
                result.add(new DocumentChunk(
                        "doc-" + request.getDocumentId() + "-chunk-" + chunkIndex,
                        null,
                        ChunkType.NORMAL,
                        section.headingPath(),
                        chunkIndex++,
                        parts.get(partIndex),
                        brotherChunkId,
                        brotherChunkId == null ? null : partIndex + 1,
                        brotherChunkId == null ? null : parts.size()
                ));
            }
        }
        return result;
    }

    private List<Section> parseSections(String markdown, int titleLevel) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }
        List<Section> sections = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        String currentPath = null;
        boolean inFence = false;
        String fence = null;

        for (String line : markdown.split("\\n", -1)) {
            Matcher fenceMatcher = FENCE.matcher(line);
            if (fenceMatcher.find()) {
                String marker = fenceMatcher.group(1);
                if (!inFence) {
                    inFence = true;
                    fence = marker;
                } else if (marker.equals(fence)) {
                    inFence = false;
                    fence = null;
                }
                append(content, line);
                continue;
            }

            Matcher headingMatcher = HEADING.matcher(line);
            if (!inFence && headingMatcher.matches()
                    && headingMatcher.group(1).length() <= titleLevel) {
                flush(sections, currentPath, content);
                int level = headingMatcher.group(1).length();
                while (headings.size() >= level) {
                    headings.removeLast();
                }
                while (headings.size() < level - 1) {
                    headings.add("");
                }
                headings.add(headingMatcher.group(2).trim());
                currentPath = headings.stream()
                        .filter(StringUtils::hasText)
                        .reduce((left, right) -> left + " > " + right)
                        .orElse(null);
                continue;
            }
            append(content, line);
        }
        flush(sections, currentPath, content);
        return sections;
    }

    private void append(StringBuilder content, String line) {
        if (!content.isEmpty()) {
            content.append('\n');
        }
        content.append(line);
    }

    private void flush(List<Section> sections, String headingPath, StringBuilder content) {
        String text = TextChunkSupport.normalize(content.toString());
        if (StringUtils.hasText(text)) {
            sections.add(new Section(headingPath, text));
        }
        content.setLength(0);
    }

    private record Section(String headingPath, String text) {
    }
}
