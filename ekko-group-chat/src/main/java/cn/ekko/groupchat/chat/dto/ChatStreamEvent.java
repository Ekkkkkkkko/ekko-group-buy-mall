package cn.ekko.groupchat.chat.dto;

/** SSE 流式 RAG 事件；type 同时作为 SSE event name。 */
public record ChatStreamEvent(String type, Object data) {

    public static ChatStreamEvent progress(String message) {
        return new ChatStreamEvent("PROGRESS", message);
    }

    public static ChatStreamEvent reference(Object sources) {
        return new ChatStreamEvent("REFERENCE", sources);
    }

    public static ChatStreamEvent answer(String delta) {
        return new ChatStreamEvent("ANSWER", delta);
    }

    public static ChatStreamEvent complete(ChatResponse response) {
        return new ChatStreamEvent("COMPLETE", response);
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent("ERROR", message);
    }
}
