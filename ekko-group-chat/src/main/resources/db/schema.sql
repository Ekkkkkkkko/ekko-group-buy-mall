CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文档主键',
    title VARCHAR(200) NOT NULL COMMENT '展示标题',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type VARCHAR(100) NOT NULL COMMENT '文件 MIME 类型',
    file_size BIGINT UNSIGNED NOT NULL COMMENT '文件字节数',
    sha256 CHAR(64) NOT NULL COMMENT '内容摘要，用于重复检测',
    original_object_key VARCHAR(500) DEFAULT NULL COMMENT 'OSS 原文件 object key',
    parsed_object_key VARCHAR(500) DEFAULT NULL COMMENT 'OSS MinerU 原始 Markdown object key',
    processed_object_key VARCHAR(500) DEFAULT NULL COMMENT 'OSS 图片改写后 Markdown object key',
    product_model VARCHAR(100) NOT NULL DEFAULT '' COMMENT '产品型号，例如 TL-7DR5130',
    chunk_strategy VARCHAR(16) NOT NULL DEFAULT 'SMART' COMMENT 'SMART/TITLE/BROTHER/EXCEL/LENGTH/SEPARATOR/REGEX',
    preprocess_version VARCHAR(32) NOT NULL DEFAULT '' COMMENT '文档预处理规则版本',
    chunk_version VARCHAR(32) NOT NULL DEFAULT '' COMMENT '分片规则版本',
    image_process_version VARCHAR(32) NOT NULL DEFAULT '' COMMENT '图片描述与改写规则版本',
    status VARCHAR(32) NOT NULL COMMENT 'UPLOADING/PARSING/CHUNKING/CHUNKED/INDEXING/PUBLISHED/FAILED',
    chunk_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已写入 ES 的分块数',
    image_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'MinerU 结果包中已持久化图片数',
    failure_reason VARCHAR(1000) DEFAULT NULL COMMENT '最近一次失败原因',
    failure_stage VARCHAR(32) DEFAULT NULL COMMENT '失败阶段，用于定向补偿',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '补偿重试次数',
    last_retry_at DATETIME(3) DEFAULT NULL COMMENT '最近一次补偿投递时间',
    mineru_task_id VARCHAR(100) DEFAULT NULL COMMENT 'MinerU 官网解析任务 ID',
    mineru_submitted_at DATETIME(3) DEFAULT NULL COMMENT 'MinerU 任务提交时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_document_sha256 (sha256),
    KEY idx_knowledge_document_status_updated (status, updated_at),
    KEY idx_knowledge_document_mineru_task (status, mineru_submitted_at),
    KEY idx_knowledge_document_product_model (product_model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG 文档元数据与处理状态';

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    chunk_id VARCHAR(120) NOT NULL COMMENT '确定性分片 ID，重复索引时保持不变',
    document_id BIGINT UNSIGNED NOT NULL COMMENT '所属文档 ID',
    parent_chunk_id VARCHAR(120) DEFAULT NULL COMMENT '父分片 ID，普通分片和父分片为空',
    brother_chunk_id VARCHAR(120) DEFAULT NULL COMMENT '兄弟分片组 ID',
    brother_chunk_index INT DEFAULT NULL COMMENT '兄弟组内序号',
    brother_chunk_total INT DEFAULT NULL COMMENT '兄弟组分片总数',
    chunk_type VARCHAR(16) NOT NULL COMMENT 'NORMAL/PARENT/CHILD',
    heading_path VARCHAR(1000) DEFAULT NULL COMMENT 'Markdown 标题路径',
    chunk_index INT NOT NULL COMMENT '可检索分片顺序，父分片为 -1',
    searchable TINYINT(1) NOT NULL COMMENT '1=写入 ES，0=只在 MySQL 保存上下文',
    content MEDIUMTEXT NOT NULL COMMENT '分片完整正文',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (chunk_id),
    KEY idx_knowledge_chunk_document (document_id, chunk_index),
    KEY idx_knowledge_chunk_parent (parent_chunk_id),
    KEY idx_knowledge_chunk_brother (brother_chunk_id, brother_chunk_index),
    CONSTRAINT fk_knowledge_chunk_document
        FOREIGN KEY (document_id) REFERENCES knowledge_document (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库结构化分片与父子关系';

CREATE TABLE IF NOT EXISTS knowledge_image (
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
    status VARCHAR(32) NOT NULL COMMENT 'STORED/DESCRIBED/DESCRIPTION_FAILED',
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

CREATE TABLE IF NOT EXISTS knowledge_chunk_image (
    chunk_id VARCHAR(120) NOT NULL COMMENT '知识分片 ID',
    image_id BIGINT UNSIGNED NOT NULL COMMENT '知识图片 ID',
    PRIMARY KEY (chunk_id, image_id),
    KEY idx_knowledge_chunk_image_image (image_id, chunk_id),
    CONSTRAINT fk_knowledge_chunk_image_chunk
        FOREIGN KEY (chunk_id) REFERENCES knowledge_chunk (chunk_id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_chunk_image_image
        FOREIGN KEY (image_id) REFERENCES knowledge_image (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识分片与图片关联';

CREATE TABLE IF NOT EXISTS admin_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '管理员主键',
    username VARCHAR(64) NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码摘要，禁止保存明文密码',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=启用，0=停用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库管理端账号';

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

-- 管理员账号由部署流程单独创建；这里只维护表结构，禁止写入默认密码或固定 BCrypt 摘要。
