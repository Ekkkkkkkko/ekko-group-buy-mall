package cn.ekko.groupchat.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 分片与图片的多对多关联，只负责新增和级联删除。 */
@TableName("knowledge_chunk_image")
@Getter
@Setter
public class KnowledgeChunkImage {

    private String chunkId;
    private Long imageId;
}
