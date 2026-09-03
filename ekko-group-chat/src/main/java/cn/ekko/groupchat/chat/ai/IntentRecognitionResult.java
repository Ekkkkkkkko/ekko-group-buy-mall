package cn.ekko.groupchat.chat.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** 结构化意图识别结果，由 LangChain4j AI Service 直接映射。 */
public record IntentRecognitionResult(
        @JsonPropertyDescription("简短说明分类依据，不输出详细思维过程")
        String reasoning,

        @JsonPropertyDescription("是否属于联巢商城、产品使用、订单、售后或拼团活动相关问题")
        boolean related,

        @JsonPropertyDescription("只能是：产品参数与选型、安装配置与使用、故障诊断与排查、售后与服务政策、订单与交易查询、拼团与营销活动、其他商城相关问题、闲聊与通用问答")
        String intent,

        @JsonPropertyDescription("从用户问题中提取的业务实体")
        Entities entities
) {

    public record Entities(
            @JsonPropertyDescription("产品型号，例如 TL-7DR5130") String productModel,
            @JsonPropertyDescription("订单号") String orderId,
            @JsonPropertyDescription("用户描述的故障现象") String faultDescription,
            @JsonPropertyDescription("用户询问的产品功能") String functionName,
            @JsonPropertyDescription("拼团或营销活动名称") String campaignName
    ) {
    }

    public GroupChatIntent domainIntent() {
        return GroupChatIntent.fromLabel(intent);
    }
}
