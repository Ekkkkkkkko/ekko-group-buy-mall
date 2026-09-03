package cn.ekko.groupchat.document.service.chunk;

import lombok.Getter;
/**
 * 结构化文档分片。
 *
 * <p>全部分片保存到 MySQL；父分片只承载完整上下文，普通分片和子分片生成向量并写入 ES。
 */
@Getter
public class DocumentChunk {

    private final String chunkId;
    private final String parentChunkId;
    private final ChunkType type;
    private final String headingPath;
    private final int chunkIndex;
    private final String text;
    private final String brotherChunkId;
    private final Integer brotherChunkIndex;
    private final Integer brotherChunkTotal;

    public DocumentChunk(
            String chunkId,
            String parentChunkId,
            ChunkType type,
            String headingPath,
            int chunkIndex,
            String text
    ) {
        this(chunkId, parentChunkId, type, headingPath, chunkIndex, text, null, null, null);
    }

    public DocumentChunk(
            String chunkId,
            String parentChunkId,
            ChunkType type,
            String headingPath,
            int chunkIndex,
            String text,
            String brotherChunkId,
            Integer brotherChunkIndex,
            Integer brotherChunkTotal
    ) {
        this.chunkId = chunkId;
        this.parentChunkId = parentChunkId;
        this.type = type;
        this.headingPath = headingPath;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.brotherChunkId = brotherChunkId;
        this.brotherChunkIndex = brotherChunkIndex;
        this.brotherChunkTotal = brotherChunkTotal;
    }

    public boolean isSearchable() {
        return type != ChunkType.PARENT;
    }
}
