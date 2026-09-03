package cn.ekko.groupchat.document.dto;

import cn.ekko.groupchat.document.entity.DocumentStatus;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.service.chunk.ChunkingStrategyType;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 文档详情响应体，将 {@link KnowledgeDocument} 实体转换为对外输出的只读视图，
 * 包含元信息、处理状态、分块数及失败原因。
 */
@Getter
public class DocumentResponse {

    private final Long id;
    private final String title;
    private final String fileName;
    private final String contentType;
    private final Long fileSize;
    private final String productModel;
    private final ChunkingStrategyType chunkStrategy;
    private final String preprocessVersion;
    private final String chunkVersion;
    private final String imageProcessVersion;
    private final DocumentStatus status;
    private final Integer chunkCount;
    private final Integer imageCount;
    private final String failureReason;
    private final String failureStage;
    private final Integer retryCount;
    private final LocalDateTime lastRetryAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private DocumentResponse(KnowledgeDocument document) {
        this.id = document.getId();
        this.title = document.getTitle();
        this.fileName = document.getFileName();
        this.contentType = document.getContentType();
        this.fileSize = document.getFileSize();
        this.productModel = document.getProductModel();
        this.chunkStrategy = document.getChunkStrategy();
        this.preprocessVersion = document.getPreprocessVersion();
        this.chunkVersion = document.getChunkVersion();
        this.imageProcessVersion = document.getImageProcessVersion();
        this.status = document.getStatus();
        this.chunkCount = document.getChunkCount();
        this.imageCount = document.getImageCount();
        this.failureReason = document.getFailureReason();
        this.failureStage = document.getFailureStage();
        this.retryCount = document.getRetryCount();
        this.lastRetryAt = document.getLastRetryAt();
        this.createdAt = document.getCreatedAt();
        this.updatedAt = document.getUpdatedAt();
    }

    public static DocumentResponse from(KnowledgeDocument document) {
        return new DocumentResponse(document);
    }

}
