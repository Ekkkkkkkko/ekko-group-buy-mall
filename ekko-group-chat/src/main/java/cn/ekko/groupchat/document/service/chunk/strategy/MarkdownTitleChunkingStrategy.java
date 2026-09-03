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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 Markdown 标题组织章节，并为超长章节生成父子分片。
 *
 * <p>子分片负责精确向量召回；命中后由检索器换回父分片，从而实现“小块检索、大块回答”。
 */
@Component
public class MarkdownTitleChunkingStrategy implements ChunkingStrategy {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");

    @Override
    public ChunkingStrategyType type() {
        return ChunkingStrategyType.TITLE;
    }

    @Override
    public List<DocumentChunk> split(ChunkingRequest request) {
        String markdown = TextChunkSupport.normalize(request.getMarkdown());
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }

        List<MarkdownSection> sections = mergeSmallSections(
                parseSections(markdown, request.getTitleLevel()),
                request.getSmallChunkMergeThreshold(),
                request.getChunkSize()
        );
        List<DocumentChunk> result = new ArrayList<>();
        int searchableIndex = 0;
        int parentIndex = 0;

        for (MarkdownSection section : sections) {
            String sectionText = TextChunkSupport.normalize(section.text());
            if (!StringUtils.hasText(sectionText)) {
                continue;
            }
            if (sectionText.length() <= request.getChunkSize()) {
                result.add(normalChunk(request, searchableIndex++, section.headingPath(), sectionText));
                continue;
            }

            List<String> parentTexts = TextChunkSupport.splitNaturally(
                    sectionText,
                    request.getParentChunkSize(),
                    0
            );
            for (int partIndex = 0; partIndex < parentTexts.size(); partIndex++) {
                String parentText = parentTexts.get(partIndex);
                String parentId = "doc-" + request.getDocumentId() + "-parent-" + parentIndex++;
                result.add(new DocumentChunk(
                        parentId,
                        null,
                        ChunkType.PARENT,
                        section.headingPath(),
                        -1,
                        parentText
                ));

                List<String> childTexts = TextChunkSupport.splitNaturally(
                        parentText,
                        request.getChunkSize(),
                        request.getChunkOverlap()
                );
                for (int childIndex = 0; childIndex < childTexts.size(); childIndex++) {
                    String childId = parentId + "-child-" + childIndex;
                    result.add(new DocumentChunk(
                            childId,
                            parentId,
                            ChunkType.CHILD,
                            section.headingPath(),
                            searchableIndex++,
                            childTexts.get(childIndex)
                    ));
                }
            }
        }
        return result;
    }

    /** SMART 策略使用同一规则判断 MinerU Markdown 是否存在可用标题结构。 */
    public boolean hasStructuralHeading(String markdown, int titleLevel) {
        String normalized = TextChunkSupport.normalize(markdown);
        boolean inFence = false;
        for (String line : normalized.split("\\n", -1)) {
            if (FENCE.matcher(line).find()) {
                inFence = !inFence;
                continue;
            }
            Matcher matcher = HEADING.matcher(line);
            if (!inFence && matcher.matches() && matcher.group(1).length() <= titleLevel) {
                return true;
            }
        }
        return false;
    }

    private List<MarkdownSection> parseSections(String markdown, int titleLevel) {
        List<MarkdownSection> sections = new ArrayList<>();
        String[] headingStack = new String[6];
        StringBuilder current = new StringBuilder();
        String currentPath = null;
        boolean inFence = false;

        for (String line : markdown.split("\\n", -1)) {
            if (FENCE.matcher(line).find()) {
                inFence = !inFence;
            }
            Matcher matcher = HEADING.matcher(line);
            boolean isSectionHeading = !inFence
                    && matcher.matches()
                    && matcher.group(1).length() <= titleLevel;
            if (isSectionHeading) {
                addSection(sections, currentPath, current);
                int level = matcher.group(1).length();
                headingStack[level - 1] = matcher.group(2).trim();
                Arrays.fill(headingStack, level, headingStack.length, null);
                currentPath = buildHeadingPath(headingStack, level);
                continue;
            }
            current.append(line).append('\n');
        }
        addSection(sections, currentPath, current);
        return sections;
    }

    private void addSection(List<MarkdownSection> sections, String headingPath, StringBuilder current) {
        String text = TextChunkSupport.normalize(current.toString());
        if (StringUtils.hasText(text)) {
            sections.add(new MarkdownSection(headingPath, text));
        }
        current.setLength(0);
    }

    private String buildHeadingPath(String[] headingStack, int level) {
        List<String> headings = new ArrayList<>();
        for (int index = 0; index < level; index++) {
            if (StringUtils.hasText(headingStack[index])) {
                headings.add(headingStack[index]);
            }
        }
        return String.join(" > ", headings);
    }

    private DocumentChunk normalChunk(
            ChunkingRequest request,
            int chunkIndex,
            String headingPath,
            String text
    ) {
        return new DocumentChunk(
                "doc-" + request.getDocumentId() + "-chunk-" + chunkIndex,
                null,
                ChunkType.NORMAL,
                headingPath,
                chunkIndex,
                text
        );
    }

    private List<MarkdownSection> mergeSmallSections(
            List<MarkdownSection> sections,
            int threshold,
            int maxSize
    ) {
        if (threshold <= 0 || sections.size() < 2) {
            return sections;
        }
        List<MarkdownSection> merged = new ArrayList<>(sections);
        int index = 0;
        while (index < merged.size()) {
            MarkdownSection current = merged.get(index);
            if (current.text().length() >= threshold) {
                index++;
                continue;
            }
            if (index == 0 && canMerge(current, merged.get(1), maxSize)) {
                MarkdownSection next = merged.get(1);
                merged.set(1, new MarkdownSection(
                        next.headingPath(),
                        current.text() + "\n\n" + next.text()
                ));
                merged.remove(0);
                continue;
            }
            if (index > 0 && canMerge(merged.get(index - 1), current, maxSize)) {
                MarkdownSection previous = merged.get(index - 1);
                merged.set(index - 1, new MarkdownSection(
                        previous.headingPath(),
                        previous.text() + "\n\n" + current.text()
                ));
                merged.remove(index);
                index = Math.max(0, index - 1);
                continue;
            }
            if (index + 1 < merged.size() && canMerge(current, merged.get(index + 1), maxSize)) {
                MarkdownSection next = merged.get(index + 1);
                merged.set(index + 1, new MarkdownSection(
                        StringUtils.hasText(current.headingPath()) ? current.headingPath() : next.headingPath(),
                        current.text() + "\n\n" + next.text()
                ));
                merged.remove(index);
                continue;
            }
            index++;
        }
        return merged;
    }

    private boolean canMerge(MarkdownSection left, MarkdownSection right, int maxSize) {
        return left.text().length() + 2 + right.text().length() <= maxSize;
    }

    /** 标题解析过程中的内部值对象，不暴露给其他层。 */
    private static class MarkdownSection {

        private final String headingPath;
        private final String text;

        private MarkdownSection(String headingPath, String text) {
            this.headingPath = headingPath;
            this.text = text;
        }

        private String headingPath() {
            return headingPath;
        }

        private String text() {
            return text;
        }
    }
}
