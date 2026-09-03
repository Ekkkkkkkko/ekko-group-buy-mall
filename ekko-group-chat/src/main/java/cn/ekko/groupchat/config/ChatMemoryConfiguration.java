package cn.ekko.groupchat.config;

import cn.ekko.groupchat.chat.memory.DatabaseChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfiguration {

    @Bean
    ChatMemoryProvider chatMemoryProvider(
            DatabaseChatMemoryStore memoryStore,
            GroupChatProperties properties
    ) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(properties.getRetrieval().getMemoryMaxMessages())
                .chatMemoryStore(memoryStore)
                .build();
    }
}
