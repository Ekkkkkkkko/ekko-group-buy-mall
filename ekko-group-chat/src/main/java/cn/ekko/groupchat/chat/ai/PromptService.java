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

    private static final String ANSWER_PRESENTATION_POLICY = """

            回答要求：先直接回答问题，再给必要步骤或注意事项；除非用户要求展开，避免罗列过多相似方案。
            正文不要插入资料编号，来源由界面下方的资料卡片统一展示。
            """;

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
            return resource.getContentAsString(StandardCharsets.UTF_8) + ANSWER_PRESENTATION_POLICY;
        } catch (IOException exception) {
            if (intent != GroupChatIntent.OTHER) {
                return getPrompt(GroupChatIntent.OTHER);
            }
            throw new IllegalStateException("默认客服提示词缺失", exception);
        }
    }
}
