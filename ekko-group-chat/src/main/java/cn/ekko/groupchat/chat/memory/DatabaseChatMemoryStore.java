package cn.ekko.groupchat.chat.memory;

import cn.ekko.groupchat.chat.persistence.entity.ChatMessageType;
import cn.ekko.groupchat.chat.persistence.service.ChatMessageService;
import cn.ekko.groupchat.config.GroupChatProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Redis 优先、MySQL 回源的会话记忆存储。消息最终持久化由应用服务负责。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "group-chat:chat-memory:";

    private final ChatMessageService chatMessageService;
    private final StringRedisTemplate redisTemplate;
    private final GroupChatProperties properties;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = key(memoryId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return new ArrayList<>(ChatMessageDeserializer.messagesFromJson(json));
            }
        } catch (RuntimeException exception) {
            log.warn("Redis 读取聊天记忆失败, memoryId={}, 回源 MySQL", memoryId, exception);
        }

        List<ChatMessage> messages = loadFromDatabase(memoryId.toString());
        save(key, messages);
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        int maxMessages = properties.getRetrieval().getMemoryMaxMessages();
        List<ChatMessage> trimmed = messages.size() <= maxMessages
                ? new ArrayList<>(messages)
                : new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
        save(key(memoryId), trimmed);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        evictCache(memoryId);
        chatMessageService.deleteByConversationId(memoryId.toString());
    }

    public void evictCache(Object memoryId) {
        try {
            redisTemplate.delete(key(memoryId));
        } catch (RuntimeException exception) {
            log.warn("Redis 清除聊天记忆失败, memoryId={}", memoryId, exception);
        }
    }

    private List<ChatMessage> loadFromDatabase(String conversationId) {
        int maxMessages = properties.getRetrieval().getMemoryMaxMessages();
        return chatMessageService.recentHistory(conversationId, maxMessages).stream()
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .map(message -> message.getType() == ChatMessageType.USER
                        ? UserMessage.from(message.getContent())
                        : AiMessage.from(message.getContent()))
                .map(message -> (ChatMessage) message)
                .toList();
    }

    private void save(String key, List<ChatMessage> messages) {
        try {
            redisTemplate.opsForValue().set(
                    key,
                    ChatMessageSerializer.messagesToJson(messages),
                    properties.getRetrieval().getMemoryTtl()
            );
        } catch (RuntimeException exception) {
            log.warn("Redis 保存聊天记忆失败, key={}", key, exception);
        }
    }

    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}
