package cn.ekko.groupchat.document.entity;

/**
 * 文档处理状态枚举，描述入库流水线的状态流转：
 * UPLOADING（上传中）→ PARSING（解析中）→ CHUNKING（切片中）
 * → CHUNKED（切片已提交）→ INDEXING（向量化入库中）
 * → PUBLISHED（可检索），任一阶段失败转为 FAILED。
 */
public enum DocumentStatus {
    UPLOADING,
    PARSING,
    CHUNKING,
    CHUNKED,
    INDEXING,
    PUBLISHED,
    FAILED
}
