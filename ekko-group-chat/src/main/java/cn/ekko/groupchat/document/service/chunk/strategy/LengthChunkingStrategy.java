package cn.ekko.groupchat.document.service.chunk.strategy;

import cn.ekko.groupchat.document.service.chunk.ChunkType;
import cn.ekko.groupchat.document.service.chunk.ChunkingRequest;
import cn.ekko.groupchat.document.service.chunk.ChunkingStrategy;
import cn.ekko.groupchat.document.service.chunk.ChunkingStrategyType;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import cn.ekko.groupchat.document.service.chunk.TextChunkSupport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 按自然边界、最大长度和重叠窗口切分。 */
@Component
public class LengthChunkingStrategy implements ChunkingStrategy {

    @Override
    public ChunkingStrategyType type() {
        return ChunkingStrategyType.LENGTH;
    }

    @Override
    public List<DocumentChunk> split(ChunkingRequest request) {
        List<String> texts = TextChunkSupport.splitNaturally(
                request.getMarkdown(),
                request.getChunkSize(),
                request.getChunkOverlap()
        );
        List<DocumentChunk> chunks = new ArrayList<>(texts.size());
        for (int index = 0; index < texts.size(); index++) {
            chunks.add(new DocumentChunk(
                    "doc-" + request.getDocumentId() + "-chunk-" + index,
                    null,
                    ChunkType.NORMAL,
                    null,
                    index,
                    texts.get(index)
            ));
        }
        return chunks;
    }
}
