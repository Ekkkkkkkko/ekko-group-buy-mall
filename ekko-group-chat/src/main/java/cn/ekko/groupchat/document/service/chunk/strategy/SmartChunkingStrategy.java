package cn.ekko.groupchat.document.service.chunk.strategy;

import cn.ekko.groupchat.document.service.chunk.ChunkingRequest;
import cn.ekko.groupchat.document.service.chunk.ChunkingStrategy;
import cn.ekko.groupchat.document.service.chunk.ChunkingStrategyType;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * KnowEngine 语义的 SMART 策略：统一使用标题父子切片，overlap 固定为
 * chunkSize 的 10%。没有标题时由标题切片器把全文作为一个逻辑章节处理。
 */
@Component
public class SmartChunkingStrategy implements ChunkingStrategy {

    private final MarkdownTitleChunkingStrategy titleStrategy;

    @Autowired
    public SmartChunkingStrategy(MarkdownTitleChunkingStrategy titleStrategy) {
        this.titleStrategy = titleStrategy;
    }

    /** 兼容旧单元测试和显式构造；SMART 不再使用长度策略分支。 */
    public SmartChunkingStrategy(
            MarkdownTitleChunkingStrategy titleStrategy,
            LengthChunkingStrategy ignoredLengthStrategy
    ) {
        this(titleStrategy);
    }

    @Override
    public ChunkingStrategyType type() {
        return ChunkingStrategyType.SMART;
    }

    @Override
    public List<DocumentChunk> split(ChunkingRequest request) {
        int smartOverlap = Math.max(0, request.getChunkSize() / 10);
        return titleStrategy.split(ChunkingRequest.builder()
                .documentId(request.getDocumentId())
                .markdown(request.getMarkdown())
                .chunkSize(request.getChunkSize())
                .chunkOverlap(smartOverlap)
                .parentChunkSize(request.getParentChunkSize())
                .titleLevel(request.getTitleLevel())
                .separator(request.getSeparator())
                .regex(request.getRegex())
                .smallChunkMergeThreshold(request.getSmallChunkMergeThreshold())
                .build());
    }
}
