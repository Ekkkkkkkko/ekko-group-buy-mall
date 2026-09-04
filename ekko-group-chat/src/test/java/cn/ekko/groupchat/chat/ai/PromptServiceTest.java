package cn.ekko.groupchat.chat.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptServiceTest {

    private final PromptService promptService = new PromptService();

    @Test
    void answerPromptsDelegateSourcesToReferenceCardsInsteadOfInlineMarkers() {
        for (GroupChatIntent intent : GroupChatIntent.values()) {
            String prompt = promptService.getPrompt(intent);

            assertThat(prompt)
                    .as("prompt for %s", intent)
                    .doesNotContain("[资料")
                    .contains("资料卡片");
        }
    }
}
