CREATE TABLE IF NOT EXISTS chat_conversation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话唯一标识，同时作为 ChatMemory ID',
    client_id VARCHAR(128) NOT NULL COMMENT '客户端标识；接入统一认证后替换为可信用户ID',
    title VARCHAR(512) DEFAULT NULL COMMENT '会话标题',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_conversation_id (conversation_id),
    KEY idx_chat_conversation_client_updated (client_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI客服会话';

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    message_id VARCHAR(64) NOT NULL COMMENT '消息唯一标识',
    conversation_id VARCHAR(64) NOT NULL COMMENT '所属会话',
    type VARCHAR(32) NOT NULL COMMENT 'USER/ASSISTANT',
    content LONGTEXT DEFAULT NULL COMMENT '消息正文',
    transform_content LONGTEXT DEFAULT NULL COMMENT '查询改写结果',
    token_count INT DEFAULT NULL COMMENT '模型Token数',
    model_name VARCHAR(128) DEFAULT NULL COMMENT '生成模型',
    rag_references JSON DEFAULT NULL COMMENT 'RAG引用、分数和召回来源',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_message_id (message_id),
    KEY idx_chat_message_conversation_created (conversation_id, created_at),
    CONSTRAINT fk_chat_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES chat_conversation (conversation_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI客服消息及检索引用';
