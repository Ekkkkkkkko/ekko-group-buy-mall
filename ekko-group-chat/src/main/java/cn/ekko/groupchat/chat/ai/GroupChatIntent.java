package cn.ekko.groupchat.chat.ai;

/** 联巢商城智能客服的业务意图及其专用系统提示词。 */
public enum GroupChatIntent {

    PRODUCT_INFO("产品参数与选型", "product-info-prompt.txt"),
    INSTALLATION("安装配置与使用", "installation-prompt.txt"),
    TROUBLESHOOTING("故障诊断与排查", "troubleshooting-prompt.txt"),
    AFTER_SALES("售后与服务政策", "after-sales-prompt.txt"),
    ORDER_TRANSACTION("订单与交易查询", "order-transaction-prompt.txt"),
    PROMOTION("拼团与营销活动", "promotion-prompt.txt"),
    OTHER("其他商城相关问题", "other-query-prompt.txt");

    private final String label;
    private final String promptFile;

    GroupChatIntent(String label, String promptFile) {
        this.label = label;
        this.promptFile = promptFile;
    }

    public String label() {
        return label;
    }

    public String promptFile() {
        return promptFile;
    }

    public static GroupChatIntent fromLabel(String label) {
        for (GroupChatIntent intent : values()) {
            if (intent.label.equals(label)) {
                return intent;
            }
        }
        return OTHER;
    }
}
