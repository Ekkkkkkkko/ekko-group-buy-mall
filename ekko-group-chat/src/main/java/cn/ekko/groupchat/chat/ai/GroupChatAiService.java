package cn.ekko.groupchat.chat.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/** 按请求绑定领域 Prompt、ChatMemory 与 RetrievalAugmentor 的最终 RAG 代理接口。 */
public interface GroupChatAiService {

    Result<String> chat(@MemoryId String conversationId, @UserMessage String message);

    Flux<String> streamChat(@MemoryId String conversationId, @UserMessage String message);
}
