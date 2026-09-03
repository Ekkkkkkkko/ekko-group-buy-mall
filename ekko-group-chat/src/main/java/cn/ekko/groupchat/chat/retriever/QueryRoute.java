package cn.ekko.groupchat.chat.retriever;

/** KnowEngine 查询路由支持的三类真实数据源。 */
public enum QueryRoute {
    RELATIONAL_DB("relational_db"),
    GRAPH_DB("graph_db"),
    KNOWLEDGE_BASE("knowledge_base");

    private final String value;

    QueryRoute(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static QueryRoute from(String value) {
        for (QueryRoute route : values()) {
            if (route.value.equalsIgnoreCase(value)) {
                return route;
            }
        }
        throw new IllegalArgumentException("不支持的查询路由: " + value);
    }
}
