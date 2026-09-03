package cn.ekko.groupchat.chat.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("chat_conversation")
@Getter
@Setter
public class ChatConversation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String clientId;
    private String title;
    private ChatConversationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Boolean deleted;
}
