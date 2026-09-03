package cn.ekko.groupchat.document.entity;

import cn.ekko.groupchat.document.service.chunk.ChunkingStrategyType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识文档实体，映射 {@code knowledge_document} 表。
 *
 * <p>存储文档元信息（标题、文件名、SHA-256 去重指纹）、OSS 对象路径
 * （原始文件与解析后 Markdown）、处理状态及 MinerU 任务关联字段。
 */
@TableName("knowledge_document")
@Getter
@Setter
public class KnowledgeDocument {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String title;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String originalObjectKey;
    private String parsedObjectKey;
    private String processedObjectKey;
    private String productModel;
    private ChunkingStrategyType chunkStrategy;
    private String preprocessVersion;
    private String chunkVersion;
    private String imageProcessVersion;
    private DocumentStatus status;
    private Integer chunkCount;
    private Integer imageCount;
    private String failureReason;
    private String failureStage;
    private Integer retryCount;
    private LocalDateTime lastRetryAt;
    private String mineruTaskId;
    private LocalDateTime mineruSubmittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
