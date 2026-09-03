package cn.ekko.groupchat.document.job;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.entity.DocumentStatus;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.mapper.KnowledgeDocumentMapper;
import cn.ekko.groupchat.document.service.DocumentPipelineService;
import cn.ekko.groupchat.document.service.DocumentPipelineLock;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** XXL-Job 与本地 Scheduler 共用的扫表补偿逻辑。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentCompensationJob {

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentPipelineService pipelineService;
    private final DocumentPipelineLock pipelineLock;
    private final GroupChatProperties properties;

    @XxlJob("documentChunkingCompensation")
    public void compensateChunking() {
        int retried = 0;
        for (KnowledgeDocument document : staleChunkingDocuments()) {
            if (!pipelineLock.isLocked("chunking", document.getId())
                    && pipelineService.retryChunking(document.getId())) {
                retried++;
            }
        }
        if (retried > 0) {
            log.info("切片补偿已重新投递 {} 个文档", retried);
        }
    }

    @XxlJob("documentIndexingCompensation")
    public void compensateIndexing() {
        int retried = 0;
        for (KnowledgeDocument document : staleIndexingDocuments()) {
            if (!pipelineLock.isLocked("indexing", document.getId())
                    && pipelineService.retryIndexing(document.getId())) {
                retried++;
            }
        }
        if (retried > 0) {
            log.info("向量化补偿已重新投递 {} 个文档", retried);
        }
    }

    public void compensateAll() {
        compensateChunking();
        compensateIndexing();
    }

    private List<KnowledgeDocument> staleChunkingDocuments() {
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.getPipeline().getStaleAfter());
        return documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                .and(wrapper -> wrapper
                        .nested(stale -> stale.eq(KnowledgeDocument::getStatus, DocumentStatus.CHUNKING)
                                .lt(KnowledgeDocument::getUpdatedAt, cutoff))
                        .or(failed -> failed.eq(KnowledgeDocument::getStatus, DocumentStatus.FAILED)
                                .eq(KnowledgeDocument::getFailureStage,
                                        DocumentPipelineService.STAGE_CHUNKING)))
                .lt(KnowledgeDocument::getRetryCount, properties.getPipeline().getMaxRetries())
                .orderByAsc(KnowledgeDocument::getUpdatedAt)
                .last("LIMIT " + properties.getPipeline().getCompensationBatchSize()));
    }

    private List<KnowledgeDocument> staleIndexingDocuments() {
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.getPipeline().getStaleAfter());
        return documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                .and(wrapper -> wrapper
                        .nested(stale -> stale.in(KnowledgeDocument::getStatus,
                                                DocumentStatus.CHUNKED, DocumentStatus.INDEXING)
                                .lt(KnowledgeDocument::getUpdatedAt, cutoff))
                        .or(failed -> failed.eq(KnowledgeDocument::getStatus, DocumentStatus.FAILED)
                                .eq(KnowledgeDocument::getFailureStage,
                                        DocumentPipelineService.STAGE_INDEXING)))
                .lt(KnowledgeDocument::getRetryCount, properties.getPipeline().getMaxRetries())
                .orderByAsc(KnowledgeDocument::getUpdatedAt)
                .last("LIMIT " + properties.getPipeline().getCompensationBatchSize()));
    }
}
