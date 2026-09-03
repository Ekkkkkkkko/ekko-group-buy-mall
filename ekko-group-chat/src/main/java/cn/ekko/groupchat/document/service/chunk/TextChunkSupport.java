package cn.ekko.groupchat.document.service.chunk;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 各切分策略共用的文本规范化和自然边界切分能力。
 */
public final class TextChunkSupport {

    private static final String SENTENCE_ENDINGS = "。！？；.!?;";

    private TextChunkSupport() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 先按 Markdown 段落和完整行形成原子块，再按大小装箱。
     *
     * <p>重叠只复用完整原子块，不再从上一块正文中间截取字符。因此表格行、列表项和
     * Markdown 标签不会出现在分片开头时只剩半截；单个超长普通段落才退化到句子边界。
     */
    public static List<String> splitNaturally(String text, int maxSize, int overlap) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        if (maxSize <= 0 || overlap < 0 || overlap >= maxSize) {
            throw new IllegalArgumentException("分块配置要求 chunkSize > chunkOverlap >= 0");
        }
        List<String> units = atomicUnits(normalized, maxSize);
        List<String> chunks = new ArrayList<>();
        int startUnit = 0;
        while (startUnit < units.size()) {
            int endUnit = startUnit;
            int currentLength = 0;
            while (endUnit < units.size()) {
                String unit = units.get(endUnit);
                int candidateLength = currentLength + (currentLength == 0 ? 0 : 2) + unit.length();
                if (currentLength > 0 && candidateLength > maxSize) {
                    break;
                }
                currentLength = candidateLength;
                endUnit++;
            }
            chunks.add(String.join("\n\n", units.subList(startUnit, endUnit)));
            if (endUnit >= units.size()) {
                break;
            }

            int nextStart = overlapStart(units, startUnit, endUnit, overlap);
            startUnit = nextStart == startUnit ? endUnit : nextStart;
        }
        return chunks;
    }

    private static List<String> atomicUnits(String text, int maxSize) {
        List<String> units = new ArrayList<>();
        for (String block : text.split("\\n\\s*\\n")) {
            String normalizedBlock = block.trim();
            if (!StringUtils.hasText(normalizedBlock)) {
                continue;
            }
            if (normalizedBlock.length() <= maxSize) {
                units.add(normalizedBlock);
                continue;
            }
            for (String line : normalizedBlock.split("\\n")) {
                String normalizedLine = line.trim();
                if (!StringUtils.hasText(normalizedLine)) {
                    continue;
                }
                // 预处理后的 HTML 表格一行就是一个列表项，宁可单行略超限也不拆单元格关系。
                if (normalizedLine.length() <= maxSize || normalizedLine.startsWith("- ")) {
                    units.add(normalizedLine);
                } else {
                    units.addAll(splitOversizedLine(normalizedLine, maxSize));
                }
            }
        }
        return units;
    }

    private static List<String> splitOversizedLine(String text, int maxSize) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int hardEnd = Math.min(start + maxSize, text.length());
            int end = findNaturalBreak(text, start, hardEnd, maxSize);
            parts.add(text.substring(start, end).trim());
            start = end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        return parts;
    }

    private static int overlapStart(List<String> units, int startUnit, int endUnit, int overlap) {
        if (overlap == 0) {
            return endUnit;
        }
        int length = 0;
        int nextStart = endUnit;
        while (nextStart > startUnit) {
            String previous = units.get(nextStart - 1);
            int candidateLength = length + (length == 0 ? 0 : 2) + previous.length();
            if (candidateLength > overlap) {
                break;
            }
            length = candidateLength;
            nextStart--;
        }
        return nextStart;
    }

    private static int findNaturalBreak(String text, int start, int hardEnd, int maxSize) {
        if (hardEnd >= text.length()) {
            return text.length();
        }
        int minimum = start + maxSize / 2;

        int paragraph = text.lastIndexOf("\n\n", hardEnd);
        if (paragraph > minimum) {
            return paragraph;
        }

        int line = text.lastIndexOf('\n', hardEnd);
        if (line > minimum) {
            return line;
        }

        for (int index = hardEnd - 1; index > minimum; index--) {
            if (SENTENCE_ENDINGS.indexOf(text.charAt(index)) >= 0) {
                return index + 1;
            }
        }

        int whitespace = text.lastIndexOf(' ', hardEnd);
        return whitespace > minimum ? whitespace : hardEnd;
    }
}
