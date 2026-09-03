package cn.ekko.groupchat.document.service.chunk;

import lombok.Builder;
import lombok.Getter;

/**
 * 单次文档切分所需的正文和策略参数。
 */
@Getter
@Builder
public class ChunkingRequest {

    private final long documentId;
    private final String markdown;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int parentChunkSize;
    private final int titleLevel;
    private final String separator;
    private final String regex;
    private final int smallChunkMergeThreshold;
}
