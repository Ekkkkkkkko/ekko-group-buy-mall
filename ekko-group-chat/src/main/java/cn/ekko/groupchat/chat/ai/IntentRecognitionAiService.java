package cn.ekko.groupchat.chat.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** RAG 之前的业务意图识别与实体抽取接口。 */
public interface IntentRecognitionAiService {

    @SystemMessage(fromResource = "prompts/intent-recognition-prompt.txt")
    IntentRecognitionResult recognize(
            @MemoryId String conversationId,
            @UserMessage String userMessage
    );
}
