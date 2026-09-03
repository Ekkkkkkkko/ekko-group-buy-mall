package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.client.AliyunOssClient;
import cn.ekko.groupchat.document.client.MineruArchiveParser;
import cn.ekko.groupchat.document.client.MineruClient;
import cn.ekko.groupchat.document.client.MineruResultDownloader;
import cn.ekko.groupchat.document.client.MineruParsedArchive;
import cn.ekko.groupchat.document.client.MineruTaskResult;
import cn.ekko.groupchat.document.entity.DocumentStatus;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.mapper.KnowledgeDocumentMapper;
import cn.ekko.groupchat.document.service.image.ImageProcessingResult;
import cn.ekko.groupchat.document.service.image.KnowledgeImageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * MinerU 解析任务处理器，仅负责保留现有轮询方式并推进 PARSING → CHUNKING。
 *
 * <p>单次处理流程：超时检查 → 查询任务状态 → 完成时下载 ZIP、提取 Markdown、
 * 上传 OSS 后发布转换完成事件；切片与向量化由独立事件监听器处理。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MineruTaskProcessor {

    private final KnowledgeDocumentMapper documentMapper;
    private final MineruClient mineruClient;
    private final MineruResultDownloader downloader;
    private final MineruArchiveParser archiveParser;
    private final AliyunOssClient ossClient;
    private final KnowledgeImageService imageService;
    private final DocumentPipelineService pipelineService;
    private final GroupChatProperties properties;
    private final Clock clock;

    public void process(KnowledgeDocument document) {
        if (document.getMineruSubmittedAt() == null) {
            markFailed(document.getId(), "MinerU 任务缺少提交时间");
            return;
        }
        LocalDateTime deadline = document.getMineruSubmittedAt()
                .plus(properties.getMineru().getTaskTimeout());
        if (!LocalDateTime.now(clock).isBefore(deadline)) {
            markFailed(document.getId(), "MinerU 解析任务超时");
            return;
        }

        MineruTaskResult task = mineruClient.queryTask(document.getMineruTaskId());
        switch (task.state()) {
            case PENDING, RUNNING, CONVERTING -> {
                return;
            }
            case FAILED -> markFailed(document.getId(), task.errorMessage());
            case DONE -> complete(document, task);
        }
    }

    private void complete(KnowledgeDocument document, MineruTaskResult task) {
        try {
            byte[] archive = downloader.download(task.fullZipUrl());
            MineruParsedArchive parsed = archiveParser.parse(archive);
            ossClient.put(
                    document.getParsedObjectKey(),
                    parsed.markdown().getBytes(StandardCharsets.UTF_8),
                    "text/markdown; charset=UTF-8"
            );
            ImageProcessingResult imageResult = imageService.process(document, parsed);
            String processedObjectKey = StringUtils.hasText(document.getProcessedObjectKey())
                    ? document.getProcessedObjectKey()
                    : processedObjectKey(document.getParsedObjectKey());
            ossClient.put(
                    processedObjectKey,
                    imageResult.processedMarkdown().getBytes(StandardCharsets.UTF_8),
                    "text/markdown; charset=UTF-8"
            );
            document.setProcessedObjectKey(processedObjectKey);
            document.setImageCount(imageResult.imageCount());
            pipelineService.startChunking(
                    document.getId(), processedObjectKey, imageResult.imageCount()
            );
        } catch (RuntimeException exception) {
            markFailed(document.getId(), rootMessage(exception));
            log.warn("MinerU task completion failed, documentId={}", document.getId(), exception);
        }
    }

    private String processedObjectKey(String rawObjectKey) {
        int extension = rawObjectKey.lastIndexOf('.');
        return extension > rawObjectKey.lastIndexOf('/')
                ? rawObjectKey.substring(0, extension) + ".processed.md"
                : rawObjectKey + ".processed.md";
    }

    private void markFailed(long documentId, String reason) {
        documentMapper.update(Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .set(KnowledgeDocument::getStatus, DocumentStatus.FAILED)
                .set(KnowledgeDocument::getFailureStage, "PARSING")
                .set(KnowledgeDocument::getFailureReason, truncate(reason, 1000)));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
