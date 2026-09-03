package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.client.ElasticsearchKnowledgeClient;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.service.chunk.ChunkQualityValidator;
import cn.ekko.groupchat.document.service.chunk.ChunkingStrategyFactory;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import cn.ekko.groupchat.document.service.chunk.ChunkingStrategyType;
import cn.ekko.groupchat.document.service.preprocess.KnowledgeMarkdownPreprocessor;
import cn.ekko.groupchat.document.service.image.KnowledgeImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 文档流水线的切片与向量化能力；状态流转和事件投递由 DocumentPipelineService 负责。 */
@Service
@RequiredArgsConstructor
public class KnowledgeIndexingService {

    private final KnowledgeMarkdownPreprocessor preprocessor;
    private final ChunkingStrategyFactory chunkingStrategyFactory;
    private final ChunkQualityValidator qualityValidator;
    private final KnowledgeChunkService chunkService;
    private final KnowledgeImageService imageService;
    private final ElasticsearchKnowledgeClient elasticsearchClient;
    private final GroupChatProperties properties;

    /** 转换产物预处理后切片，质量校验通过才整体替换 MySQL 快照。 */
    public List<DocumentChunk> chunk(KnowledgeDocument document, String rawMarkdown) {
        boolean preserveExcelHtml = document.getChunkStrategy() == ChunkingStrategyType.EXCEL
                && properties.getRag().isExcelHtmlMode();
        String processedMarkdown = preprocessor.preprocess(rawMarkdown, preserveExcelHtml);
        List<DocumentChunk> chunks = chunkingStrategyFactory.split(
                document.getId(),
                processedMarkdown,
                document.getChunkStrategy()
        );
        qualityValidator.validate(chunks, preserveExcelHtml);
        chunkService.replaceAll(document.getId(), chunks);
        imageService.linkChunks(document.getId(), chunks);
        return chunks;
    }

    /** 从 MySQL 中已经提交的分片快照向量化，避免事件阶段之间传递大正文。 */
    public int embed(KnowledgeDocument document) {
        List<DocumentChunk> chunks = chunkService.findDocumentChunks(document.getId());
        GroupChatProperties.Rag rag = properties.getRag();
        return elasticsearchClient.index(
                document.getId(),
                document.getTitle(),
                document.getProductModel(),
                document.getProcessedObjectKey() != null
                        ? document.getProcessedObjectKey()
                        : document.getParsedObjectKey(),
                rag.getPreprocessVersion(),
                rag.getChunkVersion(),
                chunks
        );
    }

    /** 兼容旧调用方的同步入口；新链路使用 chunk/embed 两个独立阶段。 */
    public int index(KnowledgeDocument document, String rawMarkdown) {
        chunk(document, rawMarkdown);
        return embed(document);
    }
}
