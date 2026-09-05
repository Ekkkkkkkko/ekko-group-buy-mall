ALTER TABLE knowledge_document
    ADD COLUMN processed_object_key VARCHAR(500) DEFAULT NULL
        COMMENT 'OSS 图片改写后 Markdown object key' AFTER parsed_object_key,
    ADD COLUMN image_process_version VARCHAR(32) NOT NULL DEFAULT ''
        COMMENT '图片描述与改写规则版本' AFTER chunk_version,
    ADD COLUMN image_count INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT 'MinerU 结果包中已持久化图片数' AFTER chunk_count;

CREATE TABLE knowledge_image (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '图片主键',
    document_id BIGINT UNSIGNED NOT NULL COMMENT '所属文档 ID',
    source_path VARCHAR(500) NOT NULL COMMENT 'MinerU ZIP 中的原始相对路径',
    object_key VARCHAR(500) NOT NULL COMMENT '私有 OSS 图片 object key',
    sha256 CHAR(64) NOT NULL COMMENT '图片内容摘要',
    content_type VARCHAR(100) NOT NULL COMMENT '校验后的图片 MIME 类型',
    file_size BIGINT UNSIGNED NOT NULL COMMENT '图片字节数',
    alt_text VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'MinerU Markdown 原始 alt 文本',
    description TEXT DEFAULT NULL COMMENT '视觉模型生成的可检索图片描述',
    description_model VARCHAR(100) NOT NULL DEFAULT '' COMMENT '视觉模型名称',
    description_version VARCHAR(32) NOT NULL DEFAULT '' COMMENT '图片描述提示词版本',
    status VARCHAR(32) NOT NULL COMMENT 'STORED/DESCRIBED/DESCRIPTION_FAILED/EXCLUDED',
    failure_reason VARCHAR(1000) DEFAULT NULL COMMENT '图片描述失败原因',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_image_document_path (document_id, source_path),
    KEY idx_knowledge_image_document (document_id, id),
    KEY idx_knowledge_image_sha256 (sha256),
    CONSTRAINT fk_knowledge_image_document
        FOREIGN KEY (document_id) REFERENCES knowledge_document (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MinerU 图片资源与视觉描述';

CREATE TABLE knowledge_chunk_image (
    chunk_id VARCHAR(120) NOT NULL COMMENT '知识分片 ID',
    image_id BIGINT UNSIGNED NOT NULL COMMENT '知识图片 ID',
    PRIMARY KEY (chunk_id, image_id),
    KEY idx_knowledge_chunk_image_image (image_id, chunk_id),
    CONSTRAINT fk_knowledge_chunk_image_chunk
        FOREIGN KEY (chunk_id) REFERENCES knowledge_chunk (chunk_id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_chunk_image_image
        FOREIGN KEY (image_id) REFERENCES knowledge_image (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识分片与图片关联';
