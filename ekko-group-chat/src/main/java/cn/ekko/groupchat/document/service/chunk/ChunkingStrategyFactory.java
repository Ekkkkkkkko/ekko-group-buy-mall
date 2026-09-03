package cn.ekko.groupchat.document.service.chunk;

import cn.ekko.groupchat.config.GroupChatProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 文档切分策略工厂。
 *
 * <p>Spring 负责收集所有 {@link ChunkingStrategy} 实现，工厂根据配置选择其中一个，
 * 调用方不需要依赖具体策略类。
 */
@Component
public class ChunkingStrategyFactory {

    private final Map<ChunkingStrategyType, ChunkingStrategy> strategies;
    private final GroupChatProperties properties;

    public ChunkingStrategyFactory(List<ChunkingStrategy> strategies, GroupChatProperties properties) {
        this.strategies = new EnumMap<>(ChunkingStrategyType.class);
        for (ChunkingStrategy strategy : strategies) {
            ChunkingStrategy previous = this.strategies.put(strategy.type(), strategy);
            if (previous != null) {
                throw new IllegalStateException("切分策略重复注册: " + strategy.type());
            }
        }
        this.properties = properties;
    }

    /**
     * 使用 application.yml 中配置的策略切分文档。
     */
    public List<DocumentChunk> split(long documentId, String markdown) {
        return split(documentId, markdown, null);
    }

    /** 使用文档指定策略；未指定时采用 application.yml 的默认策略。 */
    public List<DocumentChunk> split(
            long documentId,
            String markdown,
            ChunkingStrategyType requestedType
    ) {
        GroupChatProperties.Rag rag = properties.getRag();
        ChunkingStrategyType selectedType = requestedType == null
                ? configuredType()
                : requestedType;
        return split(selectedType, ChunkingRequest.builder()
                .documentId(documentId)
                .markdown(markdown)
                .chunkSize(rag.getChunkSize())
                .chunkOverlap(rag.getChunkOverlap())
                .parentChunkSize(rag.getParentChunkSize())
                .titleLevel(rag.getTitleLevel())
                .separator(rag.getChunkSeparator())
                .regex(rag.getChunkRegex())
                .smallChunkMergeThreshold(rag.getSmallChunkMergeThreshold())
                .build());
    }

    public ChunkingStrategyType configuredType() {
        return ChunkingStrategyType.from(properties.getRag().getChunkStrategy());
    }

    /** 显式选择策略，供单篇文档的 chunkStrategy 配置调用。 */
    public List<DocumentChunk> split(ChunkingStrategyType type, ChunkingRequest request) {
        validate(request);
        ChunkingStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalStateException("未注册切分策略: " + type);
        }
        return strategy.split(request);
    }

    private void validate(ChunkingRequest request) {
        if (request.getChunkSize() <= 0
                || request.getChunkOverlap() < 0
                || request.getChunkOverlap() >= request.getChunkSize()) {
            throw new IllegalArgumentException("分块配置要求 chunkSize > chunkOverlap >= 0");
        }
        if (request.getParentChunkSize() < request.getChunkSize()) {
            throw new IllegalArgumentException("父分块大小不能小于子分块大小");
        }
        if (request.getTitleLevel() < 1 || request.getTitleLevel() > 6) {
            throw new IllegalArgumentException("Markdown 标题级别必须在 1 到 6 之间");
        }
        if (request.getSmallChunkMergeThreshold() < 0) {
            throw new IllegalArgumentException("小分片合并阈值不能小于 0");
        }
    }
}
