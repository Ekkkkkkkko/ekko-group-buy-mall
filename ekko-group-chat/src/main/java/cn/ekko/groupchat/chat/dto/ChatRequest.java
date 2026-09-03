package cn.ekko.groupchat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 智能问答请求体，携带用户问题，不可为空。
 */
@Getter
@Setter
public class ChatRequest {

    @NotBlank(message = "问题不能为空")
    private String question;

    /** 可选会话 ID；为空时由服务端创建新会话。 */
    private String conversationId;

    /**
     * 当前聊天模块尚未接入商城登录态，暂以客户端标识隔离会话。
     * 接入统一认证后应改为由服务端从登录态解析用户 ID。
     */
    private String clientId;

}
