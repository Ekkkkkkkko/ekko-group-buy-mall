package cn.ekko.groupchat.document.service.chunk;

/**
 * 分片在父子检索模型中的角色。
 */
public enum ChunkType {
    /** 独立参与检索的普通分片。 */
    NORMAL,
    /** 保存完整上下文但不参与 Embedding 的父分片。 */
    PARENT,
    /** 参与 Embedding，命中后回填父分片的子分片。 */
    CHILD
}
