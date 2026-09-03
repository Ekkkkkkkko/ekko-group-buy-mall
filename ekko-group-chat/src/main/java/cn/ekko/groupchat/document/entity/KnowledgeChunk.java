package cn.ekko.groupchat.document.entity;

import cn.ekko.groupchat.document.service.chunk.ChunkType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** MySQL 知识分片实体，保存分片正文、顺序及父子关系。 */
@TableName("knowledge_chunk")
@Getter
@Setter
public class KnowledgeChunk {

    @TableId(value = "chunk_id", type = IdType.INPUT)
    private String chunkId;
    private Long documentId;
    private String parentChunkId;
    private String brotherChunkId;
    private Integer brotherChunkIndex;
    private Integer brotherChunkTotal;
    private ChunkType chunkType;
    private String headingPath;
    private Integer chunkIndex;
    private Boolean searchable;
    private String content;
    private LocalDateTime createdAt;
}
