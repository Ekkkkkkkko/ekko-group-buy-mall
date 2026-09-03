package cn.ekko.groupchat.chat.persistence.service;

import cn.ekko.groupchat.chat.persistence.entity.ChatConversation;
import cn.ekko.groupchat.chat.persistence.entity.ChatConversationStatus;
import cn.ekko.groupchat.chat.persistence.mapper.ChatConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatConversationService {

    private static final String ANONYMOUS_CLIENT = "anonymous";

    private final ChatConversationMapper mapper;

    public ChatConversation resolve(String clientId, String conversationId, String firstQuestion) {
        String normalizedClientId = normalizeClientId(clientId);
        if (!StringUtils.hasText(conversationId)) {
            return create(normalizedClientId, temporaryTitle(firstQuestion));
        }
        ChatConversation conversation = mapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId)
                .eq(ChatConversation::getClientId, normalizedClientId));
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在或不属于当前客户端");
        }
        return conversation;
    }

    public ChatConversation requireOwned(String clientId, String conversationId) {
        return resolve(clientId, conversationId, "");
    }

    public ChatConversation create(String clientId, String title) {
        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId(UUID.randomUUID().toString().replace("-", ""));
        conversation.setClientId(normalizeClientId(clientId));
        conversation.setTitle(title);
        conversation.setStatus(ChatConversationStatus.ACTIVE);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversation.setDeleted(false);
        mapper.insert(conversation);
        return conversation;
    }

    public void updateTitle(String conversationId, String title) {
        if (!StringUtils.hasText(title)) {
            return;
        }
        ChatConversation update = new ChatConversation();
        update.setTitle(title.trim());
        update.setUpdatedAt(LocalDateTime.now());
        mapper.update(update, new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId));
    }

    public void touch(String conversationId) {
        ChatConversation update = new ChatConversation();
        update.setUpdatedAt(LocalDateTime.now());
        mapper.update(update, new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId));
    }

    public List<ChatConversation> list(String clientId) {
        return mapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getClientId, normalizeClientId(clientId))
                .orderByDesc(ChatConversation::getUpdatedAt));
    }

    public boolean delete(String clientId, String conversationId) {
        return mapper.delete(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId)
                .eq(ChatConversation::getClientId, normalizeClientId(clientId))) > 0;
    }

    private String normalizeClientId(String clientId) {
        return StringUtils.hasText(clientId) ? clientId.trim() : ANONYMOUS_CLIENT;
    }

    private String temporaryTitle(String question) {
        String normalized = StringUtils.hasText(question) ? question.trim() : "新对话";
        return normalized.substring(0, Math.min(normalized.length(), 20));
    }
}
