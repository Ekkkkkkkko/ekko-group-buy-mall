package cn.ekko.groupchat.document.entity;

/** 图片资源从 OSS 持久化到视觉描述生成的处理状态。 */
public enum KnowledgeImageStatus {
    STORED,
    DESCRIBED,
    DESCRIPTION_FAILED
}
