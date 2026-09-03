package cn.ekko.groupchat.document.service.chunk;

import java.util.List;

/**
 * 文档切分策略接口。每种策略只负责一种切分规则，工厂负责选择策略。
 */
public interface ChunkingStrategy {

    ChunkingStrategyType type();

    List<DocumentChunk> split(ChunkingRequest request);
}
