ALTER TABLE knowledge_document
    ADD COLUMN mineru_task_id VARCHAR(100) DEFAULT NULL COMMENT 'MinerU 官网解析任务 ID' AFTER failure_reason,
    ADD COLUMN mineru_submitted_at DATETIME(3) DEFAULT NULL COMMENT 'MinerU 任务提交时间' AFTER mineru_task_id,
    ADD KEY idx_knowledge_document_mineru_task (status, mineru_submitted_at);
