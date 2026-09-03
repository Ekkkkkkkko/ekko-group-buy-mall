package cn.ekko.groupchat.chat.controller;

import cn.ekko.groupchat.chat.dto.ChatRequest;
import cn.ekko.groupchat.chat.dto.ChatResponse;
import cn.ekko.groupchat.chat.dto.ChatStreamEvent;
import cn.ekko.groupchat.chat.persistence.entity.ChatConversation;
import cn.ekko.groupchat.chat.persistence.entity.ChatMessage;
import cn.ekko.groupchat.chat.service.ChatApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能问答控制器，提供 {@code POST /api/v1/chat} 接口，
 * 接收用户问题并返回基于知识库的 RAG 回答及引用来源。
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatApplicationService chatService;

    public ChatController(ChatApplicationService chatService) {
        this.chatService = chatService;
    }

    /**
     * 知识库智能问答。
     * <p>接收用户问题，检索知识库相关分块后经大模型生成 RAG 回答，并附带引用来源。
     *
     * @param request 问答请求，包含用户提问内容
     * @return RAG 回答及引用来源列表
     */
    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    /**
     * 流式 RAG。事件顺序为 PROGRESS → REFERENCE → ANSWER... → COMPLETE，异常为 ERROR。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatStreamEvent> stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request);
    }

    @GetMapping("/conversations")
    public List<ChatConversation> listConversations(
            @RequestParam(defaultValue = "anonymous") String clientId
    ) {
        return chatService.listConversations(clientId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessage> listMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "anonymous") String clientId
    ) {
        return chatService.listMessages(clientId, conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public boolean deleteConversation(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "anonymous") String clientId
    ) {
        return chatService.deleteConversation(clientId, conversationId);
    }
}
