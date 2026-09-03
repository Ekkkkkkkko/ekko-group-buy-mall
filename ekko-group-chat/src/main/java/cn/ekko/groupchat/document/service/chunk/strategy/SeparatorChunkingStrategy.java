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
import java.util.regex.Pattern;

/** 按固定分隔符切分，超长部分继续按自然边界切分。 */
@Component
public class SeparatorChunkingStrategy implements ChunkingStrategy {

    @Override
    public ChunkingStrategyType type() {
        return ChunkingStrategyType.SEPARATOR;
    }

    @Override
    public List<DocumentChunk> split(ChunkingRequest request) {
        if (!StringUtils.hasText(request.getSeparator())) {
            throw new IllegalArgumentException("SEPARATOR 策略必须配置 chunkSeparator");
        }
        String normalized = TextChunkSupport.normalize(request.getMarkdown());
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        return toChunks(request, Pattern.compile(Pattern.quote(request.getSeparator())).split(normalized));
    }

    private List<DocumentChunk> toChunks(ChunkingRequest request, String[] parts) {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (String part : parts) {
            for (String text : TextChunkSupport.splitNaturally(
                    part,
                    request.getChunkSize(),
                    request.getChunkOverlap()
            )) {
                int index = chunks.size();
                chunks.add(new DocumentChunk(
                        "doc-" + request.getDocumentId() + "-chunk-" + index,
                        null,
                        ChunkType.NORMAL,
                        null,
                        index,
                        text
                ));
            }
        }
        return chunks;
    }
}
