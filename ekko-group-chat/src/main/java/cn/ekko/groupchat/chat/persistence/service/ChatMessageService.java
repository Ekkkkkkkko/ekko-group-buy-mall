package cn.ekko.groupchat.chat.persistence.service;

import cn.ekko.groupchat.chat.persistence.entity.ChatMessage;
import cn.ekko.groupchat.chat.persistence.entity.ChatMessageType;
import cn.ekko.groupchat.chat.persistence.mapper.ChatMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageMapper mapper;

    public String saveUserMessage(String conversationId, String content) {
        ChatMessage message = newMessage(conversationId, ChatMessageType.USER);
        message.setContent(content);
        mapper.insert(message);
        return message.getMessageId();
    }

    public String saveAssistantPlaceholder(String conversationId) {
        ChatMessage message = newMessage(conversationId, ChatMessageType.ASSISTANT);
        mapper.insert(message);
        return message.getMessageId();
    }

    public void updateContent(String messageId, String content, String modelName) {
        ChatMessage update = new ChatMessage();
        update.setContent(content);
        update.setModelName(modelName);
        update.setUpdatedAt(LocalDateTime.now());
        mapper.update(update, byMessageId(messageId));
    }

    public void updateTransformContent(String messageId, String transformContent) {
        ChatMessage update = new ChatMessage();
        update.setTransformContent(transformContent);
        update.setUpdatedAt(LocalDateTime.now());
        mapper.update(update, byMessageId(messageId));
    }

    public void updateReferences(String messageId, List<ChatMessage.RagReference> references) {
        ChatMessage update = new ChatMessage();
        update.setRagReferences(references);
        update.setUpdatedAt(LocalDateTime.now());
        mapper.update(update, byMessageId(messageId));
    }

    public List<ChatMessage> list(String conversationId) {
        return mapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreatedAt));
    }

    /**
     * 加载历史轮次。当前轮会先写入 USER 和空 ASSISTANT，占据最新两条，加载时将其排除，
     * 避免 AiServices 再次把当前 UserMessage 加入记忆造成重复。
     */
    public List<ChatMessage> recentHistory(String conversationId, int limit) {
        List<ChatMessage> records = new ArrayList<>(mapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT " + (limit + 2))
        ));
        if (records.size() >= 2
                && records.get(0).getType() == ChatMessageType.ASSISTANT
                && (records.get(0).getContent() == null || records.get(0).getContent().isBlank())
                && records.get(1).getType() == ChatMessageType.USER) {
            records = new ArrayList<>(records.subList(2, records.size()));
        }
        if (records.size() > limit) {
            records = new ArrayList<>(records.subList(0, limit));
        }
        Collections.reverse(records);
        return records;
    }

    public void deleteByConversationId(String conversationId) {
        mapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId));
    }

    private ChatMessage newMessage(String conversationId, ChatMessageType type) {
        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = new ChatMessage();
        message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        message.setConversationId(conversationId);
        message.setType(type);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        message.setDeleted(false);
        return message;
    }

    private LambdaQueryWrapper<ChatMessage> byMessageId(String messageId) {
        return new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getMessageId, messageId);
    }
}
