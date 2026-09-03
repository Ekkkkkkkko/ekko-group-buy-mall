package cn.ekko.groupchat.chat.query;

import cn.ekko.groupchat.chat.retriever.QueryRoute;
import cn.ekko.groupchat.chat.retriever.RoutedContentRetriever;
import cn.ekko.groupchat.config.GroupChatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowEngineQueryRouterTest {

    @Test
    void routesStructuredQuestionToRegisteredRelationalRetriever() {
        FakeRetriever vector = new FakeRetriever(QueryRoute.KNOWLEDGE_BASE);
        FakeRetriever sql = new FakeRetriever(QueryRoute.RELATIONAL_DB);
        KnowEngineQueryRouter router = router("relational_db", List.of(vector, sql));

        Collection<ContentRetriever> selected = router.route(Query.from("已发布多少份文档？"));

        assertThat(selected).containsExactly(sql);
    }

    @Test
    void fallsBackToKnowledgeRetrieversWhenRouteIsNotConfigured() {
        FakeRetriever vector = new FakeRetriever(QueryRoute.KNOWLEDGE_BASE);
        FakeRetriever keyword = new FakeRetriever(QueryRoute.KNOWLEDGE_BASE);
        KnowEngineQueryRouter router = router("graph_db", List.of(vector, keyword));

        Collection<ContentRetriever> selected = router.route(Query.from("型号之间有什么关系？"));

        assertThat(selected).containsExactly(vector, keyword);
    }

    private KnowEngineQueryRouter router(String strategy, List<RoutedContentRetriever> retrievers) {
        GroupChatProperties properties = new GroupChatProperties();
        properties.getRetrieval().setQueryRoutingEnabled(true);
        ChatModel model = new JsonRouteModel(strategy);
        return new KnowEngineQueryRouter(retrievers, model, new ObjectMapper(), properties);
    }

    private record FakeRetriever(QueryRoute route) implements RoutedContentRetriever {
        @Override
        public List<Content> retrieve(Query query) {
            return List.of();
        }
    }

    private record JsonRouteModel(String strategy) implements ChatModel {
        @Override
        public dev.langchain4j.model.chat.response.ChatResponse doChat(ChatRequest request) {
            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"intent\":\"test\",\"strategy\":\""
                            + strategy + "\",\"reasoning\":\"test\",\"confidence\":0.9}"))
                    .build();
        }
    }
}
