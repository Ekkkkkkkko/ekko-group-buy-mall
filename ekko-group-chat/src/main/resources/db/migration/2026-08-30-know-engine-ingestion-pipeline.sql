ALTER TABLE knowledge_document
    MODIFY COLUMN chunk_strategy VARCHAR(16) NOT NULL DEFAULT 'SMART'
        COMMENT 'SMART/TITLE/BROTHER/EXCEL/LENGTH/SEPARATOR/REGEX',
    MODIFY COLUMN status VARCHAR(32) NOT NULL
        COMMENT 'UPLOADING/PARSING/CHUNKING/CHUNKED/INDEXING/PUBLISHED/FAILED',
    ADD COLUMN failure_stage VARCHAR(32) DEFAULT NULL
        COMMENT '失败阶段，用于定向补偿' AFTER failure_reason,
    ADD COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '补偿重试次数' AFTER failure_stage,
    ADD COLUMN last_retry_at DATETIME(3) DEFAULT NULL
        COMMENT '最近一次补偿投递时间' AFTER retry_count;

ALTER TABLE knowledge_chunk
    ADD COLUMN brother_chunk_id VARCHAR(120) DEFAULT NULL
        COMMENT '兄弟分片组 ID' AFTER parent_chunk_id,
    ADD COLUMN brother_chunk_index INT DEFAULT NULL
        COMMENT '兄弟组内序号' AFTER brother_chunk_id,
    ADD COLUMN brother_chunk_total INT DEFAULT NULL
        COMMENT '兄弟组分片总数' AFTER brother_chunk_index,
    ADD KEY idx_knowledge_chunk_brother (brother_chunk_id, brother_chunk_index);
