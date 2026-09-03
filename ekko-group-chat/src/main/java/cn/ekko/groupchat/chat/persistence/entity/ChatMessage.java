package cn.ekko.groupchat.chat.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "chat_message", autoResultMap = true)
@Getter
@Setter
public class ChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String conversationId;
    private ChatMessageType type;
    private String content;
    private String transformContent;
    private Integer tokenCount;
    private String modelName;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RagReference> ragReferences;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Boolean deleted;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagReference {
        private Long documentId;
        private String chunkId;
        private String matchedChunkId;
        private String title;
        private Integer chunkIndex;
        private String headingPath;
        private String text;
        private Double score;
        private Double rrfScore;
        private Double rerankScore;
        private String retrievalSources;
        private String contextExpansion;
    }
}
