package cn.ekko.groupchat.chat.query;

import cn.ekko.groupchat.chat.retriever.QueryRoute;
import cn.ekko.groupchat.chat.retriever.RoutedContentRetriever;
import cn.ekko.groupchat.config.GroupChatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** LLM 查询路由器，保留 KnowEngine 的关系库、图数据库、知识库三路协议。 */
@Component
@Slf4j
public class KnowEngineQueryRouter implements QueryRouter {

    private final List<RoutedContentRetriever> retrievers;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final GroupChatProperties properties;

    public KnowEngineQueryRouter(
            List<RoutedContentRetriever> retrievers,
            ChatModel chatModel,
            ObjectMapper objectMapper,
            GroupChatProperties properties
    ) {
        this.retrievers = List.copyOf(retrievers);
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        if (!properties.getRetrieval().isQueryRoutingEnabled()) {
            return knowledgeRetrievers();
        }
        try {
            QueryRoute route = decide(query.text());
            List<ContentRetriever> selected = retrievers.stream()
                    .filter(retriever -> retriever.route() == route)
                    .map(retriever -> (ContentRetriever) retriever)
                    .toList();
            if (!selected.isEmpty()) {
                log.info("查询路由完成, query={}, route={}, retrievers={}",
                        query.text(), route.value(), selected.size());
                return selected;
            }
            log.warn("查询路由 {} 尚未配置真实检索器，回退知识库", route.value());
        } catch (RuntimeException exception) {
            log.warn("查询路由失败，回退知识库, query={}", query.text(), exception);
        }
        return knowledgeRetrievers();
    }

    private QueryRoute decide(String query) {
        String response = chatModel.chat("""
                你是拼团商城的查询路由器。只分析问题应查询哪类数据源，不要回答问题。

                relational_db：订单、用户、交易等结构化字段、时间范围、数值比较或聚合查询。
                graph_db：实体关系、路径、层级、关联网络查询。
                knowledge_base：产品说明、操作方法、故障处理、政策解释等非结构化知识。

                严格输出 JSON，不得使用 Markdown：
                {"intent":"核心意图","strategy":"knowledge_base","reasoning":"判断依据","confidence":0.90}
                strategy 只能是 relational_db、graph_db、knowledge_base 之一。

                用户查询：%s
                """.formatted(query));
        String json = response.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        try {
            JsonNode root = objectMapper.readTree(json);
            return QueryRoute.from(root.path("strategy").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("无法解析查询路由结果: " + json, exception);
        }
    }

    private List<ContentRetriever> knowledgeRetrievers() {
        List<ContentRetriever> knowledge = retrievers.stream()
                .filter(retriever -> retriever.route() == QueryRoute.KNOWLEDGE_BASE)
                .map(retriever -> (ContentRetriever) retriever)
                .toList();
        if (!knowledge.isEmpty()) {
            return knowledge;
        }
        return retrievers.stream().map(retriever -> (ContentRetriever) retriever).toList();
    }
}
