ALTER TABLE knowledge_document
    ADD COLUMN preprocess_version VARCHAR(32) NOT NULL DEFAULT ''
        COMMENT '文档预处理规则版本' AFTER chunk_strategy,
    ADD COLUMN chunk_version VARCHAR(32) NOT NULL DEFAULT ''
        COMMENT '分片规则版本' AFTER preprocess_version;
