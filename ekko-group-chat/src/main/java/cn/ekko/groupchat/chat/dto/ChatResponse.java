package cn.ekko.groupchat.chat.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 智能问答响应体，包含模型回答及命中的知识来源列表。
 */
@Getter
@RequiredArgsConstructor
public class ChatResponse {

    private final String conversationId;
    private final String answer;
    private final List<ChatSourceResponse> sources;

    public ChatResponse(String answer, List<ChatSourceResponse> sources) {
        this(null, answer, sources);
    }

}
