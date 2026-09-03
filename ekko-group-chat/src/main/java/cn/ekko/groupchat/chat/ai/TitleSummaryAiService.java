package cn.ekko.groupchat.chat.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** 根据会话首问生成标题。 */
public interface TitleSummaryAiService {

    @SystemMessage("你是对话标题生成助手。根据用户第一句话生成简洁中文标题，不超过20个字，不加引号，只输出标题。")
    @UserMessage("请为以下问题生成会话标题：{{it}}")
    String generateTitle(String question);
}
