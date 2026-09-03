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

/** Excel/CSV 键值行切片：按 chunkSize 聚合完整行，任何单行都不会被截断。 */
@Component
public class ExcelChunkingStrategy implements ChunkingStrategy {

    @Override
    public ChunkingStrategyType type() {
        return ChunkingStrategyType.EXCEL;
    }

    @Override
    public List<DocumentChunk> split(ChunkingRequest request) {
        String normalized = TextChunkSupport.normalize(request.getMarkdown());
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        List<String> rows = List.of(normalized.split("\\n\\s*\\n"));
        List<String> grouped = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawRow : rows) {
            String row = rawRow.trim();
            if (!StringUtils.hasText(row)) {
                continue;
            }
            int nextLength = current.length() + (current.isEmpty() ? 0 : 2) + row.length();
            if (!current.isEmpty() && nextLength > request.getChunkSize()) {
                grouped.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(row);
        }
        if (!current.isEmpty()) {
            grouped.add(current.toString());
        }

        List<DocumentChunk> chunks = new ArrayList<>(grouped.size());
        for (int index = 0; index < grouped.size(); index++) {
            chunks.add(new DocumentChunk(
                    "doc-" + request.getDocumentId() + "-chunk-" + index,
                    null,
                    ChunkType.NORMAL,
                    "表格数据",
                    index,
                    grouped.get(index)
            ));
        }
        return chunks;
    }
}
