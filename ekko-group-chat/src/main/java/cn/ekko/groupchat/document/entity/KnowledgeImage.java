package cn.ekko.groupchat.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** MinerU 结果包中的图片资源、OSS 定位信息及视觉描述。 */
@TableName("knowledge_image")
@Getter
@Setter
public class KnowledgeImage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private String sourcePath;
    private String objectKey;
    private String sha256;
    private String contentType;
    private Long fileSize;
    private String altText;
    private String description;
    private String descriptionModel;
    private String descriptionVersion;
    private KnowledgeImageStatus status;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
