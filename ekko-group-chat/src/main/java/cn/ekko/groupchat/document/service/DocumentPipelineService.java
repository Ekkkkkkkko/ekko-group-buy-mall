package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.client.AliyunOssClient;
import cn.ekko.groupchat.document.entity.DocumentStatus;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.event.DocumentChunkedEvent;
import cn.ekko.groupchat.document.event.DocumentConvertedEvent;
import cn.ekko.groupchat.document.mapper.KnowledgeDocumentMapper;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档转换、切片、向量化三阶段的状态机。每一步只提交本阶段结果，下一步由事务后事件触发。
 */
@Service
@RequiredArgsConstructor
public class DocumentPipelineService {

    public static final String STAGE_CHUNKING = "CHUNKING";
    public static final String STAGE_INDEXING = "INDEXING";

    private final KnowledgeDocumentMapper documentMapper;
    private final AliyunOssClient ossClient;
    private final KnowledgeIndexingService indexingService;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupChatProperties properties;

    /** MinerU 或本地转换已完成：保存产物信息并在提交后触发切片。 */
    @Transactional
    public void startChunking(long documentId, String processedObjectKey, int imageCount) {
        int affectedRows = documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .in(KnowledgeDocument::getStatus, DocumentStatus.PARSING, DocumentStatus.UPLOADING)
                .set(KnowledgeDocument::getProcessedObjectKey, processedObjectKey)
                .set(KnowledgeDocument::getImageCount, imageCount)
                .set(KnowledgeDocument::getImageProcessVersion,
                        properties.getImage().getDescriptionVersion())
                .set(KnowledgeDocument::getStatus, DocumentStatus.CHUNKING)
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getFailureStage, null));
        if (affectedRows != 1) {
            throw new IllegalStateException("文档状态不允许开始切片: " + documentId);
        }
        eventPublisher.publishEvent(new DocumentConvertedEvent(documentId));
    }

    /** Markdown/TXT/Excel/CSV 本地转换完成，不经过 MinerU。 */
    @Transactional
    public void startDirectChunking(
            long documentId,
            String originalObjectKey,
            String parsedObjectKey,
            String processedObjectKey
    ) {
        int affectedRows = documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getStatus, DocumentStatus.UPLOADING)
                .set(KnowledgeDocument::getOriginalObjectKey, originalObjectKey)
                .set(KnowledgeDocument::getParsedObjectKey, parsedObjectKey)
                .set(KnowledgeDocument::getProcessedObjectKey, processedObjectKey)
                .set(KnowledgeDocument::getImageCount, 0)
                .set(KnowledgeDocument::getStatus, DocumentStatus.CHUNKING)
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getFailureStage, null));
        if (affectedRows != 1) {
            throw new IllegalStateException("文档状态不允许开始本地切片: " + documentId);
        }
        eventPublisher.publishEvent(new DocumentConvertedEvent(documentId));
    }

    /** 手工重新索引从已有 Markdown 重新执行切片和向量化，不重复调用 MinerU。 */
    @Transactional
    public void restartChunking(long documentId) {
        KnowledgeDocument document = requiredDocument(documentId);
        if (!StringUtils.hasText(document.getParsedObjectKey())) {
            throw new IllegalStateException("文档没有可用于重新索引的 Markdown: " + documentId);
        }
        int affectedRows = documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .in(KnowledgeDocument::getStatus, DocumentStatus.PUBLISHED, DocumentStatus.FAILED)
                .set(KnowledgeDocument::getStatus, DocumentStatus.CHUNKING)
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getFailureStage, null));
        if (affectedRows != 1) {
            throw new IllegalStateException("文档当前状态不允许重新索引: " + document.getStatus());
        }
        eventPublisher.publishEvent(new DocumentConvertedEvent(documentId));
    }

    /** 读取 Markdown，完成切片、质量校验和 MySQL 整体替换。 */
    @Transactional
    public void chunkAndPublish(long documentId) {
        KnowledgeDocument document = requiredDocument(documentId);
        if (document.getStatus() != DocumentStatus.CHUNKING) {
            return;
        }
        String objectKey = StringUtils.hasText(document.getProcessedObjectKey())
                ? document.getProcessedObjectKey()
                : document.getParsedObjectKey();
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalStateException("文档缺少可切片的 Markdown: " + documentId);
        }
        List<DocumentChunk> chunks = indexingService.chunk(
                document, ossClient.getText(objectKey)
        );
        int searchableCount = (int) chunks.stream().filter(DocumentChunk::isSearchable).count();
        int affectedRows = documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getStatus, DocumentStatus.CHUNKING)
                .set(KnowledgeDocument::getStatus, DocumentStatus.CHUNKED)
                .set(KnowledgeDocument::getChunkCount, searchableCount)
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getFailureStage, null));
        if (affectedRows == 1) {
            eventPublisher.publishEvent(new DocumentChunkedEvent(documentId));
        }
    }

    /** 从 MySQL 分片快照向量化，成功后发布文档。 */
    @Transactional
    public void embedAndPublish(long documentId) {
        KnowledgeDocument document = requiredDocument(documentId);
        if (document.getStatus() != DocumentStatus.CHUNKED
                && document.getStatus() != DocumentStatus.INDEXING) {
            return;
        }
        if (document.getStatus() == DocumentStatus.CHUNKED) {
            int claimed = documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                    .eq(KnowledgeDocument::getId, documentId)
                    .eq(KnowledgeDocument::getStatus, DocumentStatus.CHUNKED)
                    .set(KnowledgeDocument::getStatus, DocumentStatus.INDEXING));
            if (claimed != 1) {
                return;
            }
            document.setStatus(DocumentStatus.INDEXING);
        }
        int indexedCount = indexingService.embed(document);
        if (indexedCount <= 0) {
            throw new IllegalStateException("预处理后没有可写入 Elasticsearch 的分片");
        }
        documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getStatus, DocumentStatus.INDEXING)
                .set(KnowledgeDocument::getStatus, DocumentStatus.PUBLISHED)
                .set(KnowledgeDocument::getChunkCount, indexedCount)
                .set(KnowledgeDocument::getPreprocessVersion,
                        properties.getRag().getPreprocessVersion())
                .set(KnowledgeDocument::getChunkVersion, properties.getRag().getChunkVersion())
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getFailureStage, null));
    }

    /** 独立新事务记录失败，确保前一阶段回滚后仍能保留根因。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long documentId, String stage, String reason) {
        documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .set(KnowledgeDocument::getStatus, DocumentStatus.FAILED)
                .set(KnowledgeDocument::getFailureStage, stage)
                .set(KnowledgeDocument::getFailureReason, truncate(reason, 1000)));
    }

    /** 补偿切片：对卡住或本阶段失败的任务增加重试次数并重新投递事件。 */
    @Transactional
    public boolean retryChunking(long documentId) {
        int affectedRows = documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .and(wrapper -> wrapper.eq(KnowledgeDocument::getStatus, DocumentStatus.CHUNKING)
                        .or(nested -> nested.eq(KnowledgeDocument::getStatus, DocumentStatus.FAILED)
                                .eq(KnowledgeDocument::getFailureStage, STAGE_CHUNKING)))
                .lt(KnowledgeDocument::getRetryCount, properties.getPipeline().getMaxRetries())
                .set(KnowledgeDocument::getStatus, DocumentStatus.CHUNKING)
                .setSql("retry_count = retry_count + 1")
                .set(KnowledgeDocument::getLastRetryAt, LocalDateTime.now())
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getFailureStage, null));
        if (affectedRows == 1) {
            eventPublisher.publishEvent(new DocumentConvertedEvent(documentId));
            return true;
        }
        return false;
    }

    /** 补偿向量化：复用已落库分片，不重复转换和切片。 */
    @Transactional
    public boolean retryIndexing(long documentId) {
        int affectedRows = documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .and(wrapper -> wrapper.in(KnowledgeDocument::getStatus,
                                DocumentStatus.CHUNKED, DocumentStatus.INDEXING)
                        .or(nested -> nested.eq(KnowledgeDocument::getStatus, DocumentStatus.FAILED)
                                .eq(KnowledgeDocument::getFailureStage, STAGE_INDEXING)))
                .lt(KnowledgeDocument::getRetryCount, properties.getPipeline().getMaxRetries())
                .set(KnowledgeDocument::getStatus, DocumentStatus.CHUNKED)
                .setSql("retry_count = retry_count + 1")
                .set(KnowledgeDocument::getLastRetryAt, LocalDateTime.now())
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getFailureStage, null));
        if (affectedRows == 1) {
            eventPublisher.publishEvent(new DocumentChunkedEvent(documentId));
            return true;
        }
        return false;
    }

    private KnowledgeDocument requiredDocument(long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalStateException("文档不存在: " + documentId);
        }
        return document;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
