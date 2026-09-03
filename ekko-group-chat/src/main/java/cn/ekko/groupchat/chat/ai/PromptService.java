package cn.ekko.groupchat.chat.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 按业务意图加载并缓存最终回答阶段的系统提示词。 */
@Service
public class PromptService {

    private final Map<GroupChatIntent, String> cache = new ConcurrentHashMap<>();

    public String getPrompt(IntentRecognitionResult recognition) {
        return getPrompt(recognition.domainIntent());
    }

    public String getPrompt(GroupChatIntent intent) {
        return cache.computeIfAbsent(intent, this::load);
    }

    private String load(GroupChatIntent intent) {
        ClassPathResource resource = new ClassPathResource("prompts/" + intent.promptFile());
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            if (intent != GroupChatIntent.OTHER) {
                return getPrompt(GroupChatIntent.OTHER);
            }
            throw new IllegalStateException("默认客服提示词缺失", exception);
        }
    }
}
