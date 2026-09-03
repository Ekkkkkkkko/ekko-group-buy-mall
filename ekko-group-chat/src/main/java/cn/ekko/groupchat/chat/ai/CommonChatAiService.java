package cn.ekko.groupchat.chat.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/** 非商城问题的通用对话代理，由 Spring Starter 自动注册。 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "commonChatModel",
        streamingChatModel = "commonStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider"
)
public interface CommonChatAiService {

    @SystemMessage("你是联巢商城智能客服。用户问题与商城知识无关时可以进行简短、友好的通用交流；不得编造订单、商品、价格、活动或售后事实。")
    String chat(@MemoryId String conversationId, @UserMessage String message);

    @SystemMessage("你是联巢商城智能客服。用户问题与商城知识无关时可以进行简短、友好的通用交流；不得编造订单、商品、价格、活动或售后事实。")
    Flux<String> streamChat(@MemoryId String conversationId, @UserMessage String message);
}
