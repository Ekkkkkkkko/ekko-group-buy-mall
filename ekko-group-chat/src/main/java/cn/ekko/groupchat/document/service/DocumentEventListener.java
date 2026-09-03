package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.document.event.DocumentChunkedEvent;
import cn.ekko.groupchat.document.event.DocumentConvertedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 事务提交后异步推进文档流水线；重复事件由 Redisson 阶段锁和状态条件共同消重。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentEventListener {

    private final DocumentPipelineService pipelineService;
    private final DocumentPipelineLock pipelineLock;

    @Async("documentEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConverted(DocumentConvertedEvent event) {
        try {
            pipelineLock.execute("chunking", event.documentId(),
                    () -> pipelineService.chunkAndPublish(event.documentId()));
        } catch (RuntimeException exception) {
            pipelineService.markFailed(event.documentId(), DocumentPipelineService.STAGE_CHUNKING,
                    rootMessage(exception));
            log.warn("文档切片失败, documentId={}", event.documentId(), exception);
        }
    }

    @Async("documentEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChunked(DocumentChunkedEvent event) {
        try {
            pipelineLock.execute("indexing", event.documentId(),
                    () -> pipelineService.embedAndPublish(event.documentId()));
        } catch (RuntimeException exception) {
            pipelineService.markFailed(event.documentId(), DocumentPipelineService.STAGE_INDEXING,
                    rootMessage(exception));
            log.warn("文档向量化失败, documentId={}", event.documentId(), exception);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
