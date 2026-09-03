ALTER TABLE knowledge_document
    ADD COLUMN chunk_strategy VARCHAR(16) NOT NULL DEFAULT 'SMART'
        COMMENT 'SMART/TITLE/LENGTH/SEPARATOR/REGEX' AFTER product_model;

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    chunk_id VARCHAR(120) NOT NULL COMMENT '确定性分片 ID，重复索引时保持不变',
    document_id BIGINT UNSIGNED NOT NULL COMMENT '所属文档 ID',
    parent_chunk_id VARCHAR(120) DEFAULT NULL COMMENT '父分片 ID，普通分片和父分片为空',
    chunk_type VARCHAR(16) NOT NULL COMMENT 'NORMAL/PARENT/CHILD',
    heading_path VARCHAR(1000) DEFAULT NULL COMMENT 'Markdown 标题路径',
    chunk_index INT NOT NULL COMMENT '可检索分片顺序，父分片为 -1',
    searchable TINYINT(1) NOT NULL COMMENT '1=写入 ES，0=只在 MySQL 保存上下文',
    content MEDIUMTEXT NOT NULL COMMENT '分片完整正文',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (chunk_id),
    KEY idx_knowledge_chunk_document (document_id, chunk_index),
    KEY idx_knowledge_chunk_parent (parent_chunk_id),
    CONSTRAINT fk_knowledge_chunk_document
        FOREIGN KEY (document_id) REFERENCES knowledge_document (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库结构化分片与父子关系';
